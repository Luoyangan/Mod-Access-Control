// SPDX-License-Identifier: Apache-2.0
// Copyright 2024-2026 mcyszl.top (Luoyangan)

package mcyszl.top.mod_access_control.fabric.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import mcyszl.top.mod_access_control.core.Mac;
import mcyszl.top.mod_access_control.core.feedback.HelpText;
import mcyszl.top.mod_access_control.core.i18n.CommandText;
import mcyszl.top.mod_access_control.core.i18n.I18n;
import mcyszl.top.mod_access_control.core.model.MacConfig;
import mcyszl.top.mod_access_control.core.model.PolicyEntry;
import mcyszl.top.mod_access_control.core.model.PolicyMode;
import mcyszl.top.mod_access_control.core.model.RequiredModRule;
import mcyszl.top.mod_access_control.core.session.ViolationRecord;
import mcyszl.top.mod_access_control.core.service.MacService;
import mcyszl.top.mod_access_control.fabric.Holder;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;

import java.util.List;
import java.util.function.Consumer;

/**
 * 管理员命令 /mac（权限等级 2）。
 *
 * <p>子命令：help [页码] / status / recent / check &lt;玩家&gt; / audit &lt;玩家&gt; /
 * learn &lt;玩家&gt; &lt;whitelist|blacklist&gt; / reload / save / recheck /
 * enabled &lt;true|false&gt; / dryrun &lt;true|false&gt; /
 * mode &lt;whitelist|blacklist|switch&gt; / active &lt;whitelist|blacklist&gt; /
 * exempt list|add|remove / allowedmac list|add|remove /
 * required list|add|remove / whitelist list|add|remove / blacklist list|add|remove。</p>
 *
 * <p>v1.1：黑白名单条目支持 {@code *} 通配符与可选版本约束
 * （如 {@code /mac whitelist add sodium >=1.0}）；未设置约束 = 匹配全部版本。
 * 回显文案统一走核心 {@link CommandText}（随服务端语言切换）。</p>
 */
public final class MacCommand {

    private MacCommand() {
    }

    // ------------------------------------------------------------------ 自动补全提供器

    private static final SuggestionProvider<CommandSourceStack> SUGGEST_MODES = (ctx, b) -> {
        b.suggest("whitelist");
        b.suggest("blacklist");
        b.suggest("switch");
        return b.buildFuture();
    };

    private static final SuggestionProvider<CommandSourceStack> SUGGEST_ACTIVE = (ctx, b) -> {
        b.suggest("whitelist");
        b.suggest("blacklist");
        return b.buildFuture();
    };

    private static final SuggestionProvider<CommandSourceStack> SUGGEST_PLAYERS = (ctx, b) -> {
        ctx.getSource().getServer().getPlayerList().getPlayers().forEach(p ->
                b.suggest(p.getGameProfile().getName()));
        return b.buildFuture();
    };

    /** 延迟求值的 id 补全：按 Tab 时才读取当前配置，避免注册阶段的名单快照导致无补全。 */
    private static SuggestionProvider<CommandSourceStack> suggestIds(
            java.util.function.Supplier<List<String>> source) {
        return (ctx, b) -> {
            List<String> ids = source.get();
            if (ids != null) {
                for (String s : ids) {
                    b.suggest(s);
                }
            }
            return b.buildFuture();
        };
    }

    private static SuggestionProvider<CommandSourceStack> suggestRequiredIds() {
        return suggestIds(() -> cfg().getRequiredMods().stream().map(RequiredModRule::getId).toList());
    }

    private static SuggestionProvider<CommandSourceStack> suggestPolicyIds(boolean whitelist) {
        return suggestIds(() -> policy(cfg(), whitelist).stream().map(PolicyEntry::getId).toList());
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(
                Commands.literal("mac")
                        .requires(src -> src.hasPermission(2))
                        .executes(ctx -> run(ctx, MacCommand::status))
                        // ---- 只读
                        .then(Commands.literal("help")
                                .executes(ctx -> run(ctx, c -> help(c, 1)))
                                .then(Commands.argument("page", IntegerArgumentType.integer(1))
                                        .executes(ctx -> run(ctx, c -> help(c,
                                                IntegerArgumentType.getInteger(c, "page"))))))
                        .then(Commands.literal("status").executes(ctx -> run(ctx, MacCommand::status)))
                        .then(Commands.literal("recent").executes(ctx -> run(ctx, MacCommand::recent)))
                        .then(Commands.literal("check")
                                .then(Commands.argument("player", StringArgumentType.word())
                                        .suggests(SUGGEST_PLAYERS)
                                        .executes(ctx -> run(ctx, c -> check(
                                                c, StringArgumentType.getString(c, "player"))))))
                        // ---- 配置生命周期
                        .then(Commands.literal("reload").executes(ctx -> run(ctx, MacCommand::reload)))
                        .then(Commands.literal("save").executes(ctx -> run(ctx, MacCommand::save)))
                        .then(Commands.literal("recheck").executes(ctx -> run(ctx, MacCommand::recheck)))
                        // ---- 顶层开关 / 模式 / 试运行 / 豁免
                        .then(Commands.literal("enabled")
                                .then(Commands.argument("value", BoolArgumentType.bool())
                                        .executes(ctx -> run(ctx, c -> enabled(c,
                                                BoolArgumentType.getBool(c, "value"))))))
                        .then(Commands.literal("dryrun")
                                .then(Commands.argument("value", BoolArgumentType.bool())
                                        .executes(ctx -> run(ctx, c -> dryrun(c,
                                                BoolArgumentType.getBool(c, "value"))))))
                        .then(Commands.literal("mode")
                                .then(Commands.argument("value", StringArgumentType.word())
                                        .suggests(SUGGEST_MODES)
                                        .executes(ctx -> run(ctx, c -> mode(c,
                                                StringArgumentType.getString(c, "value"))))))
                        .then(Commands.literal("active")
                                .then(Commands.argument("value", StringArgumentType.word())
                                        .suggests(SUGGEST_ACTIVE)
                                        .executes(ctx -> run(ctx, c -> active(c,
                                                StringArgumentType.getString(c, "value"))))))
                        .then(exemptNode())
                        .then(allowedMacNode())
                        // ---- 玩家 Mod 审计 / 一键建名单
                        .then(Commands.literal("audit")
                                .then(Commands.argument("player", StringArgumentType.word())
                                        .suggests(SUGGEST_PLAYERS)
                                        .executes(ctx -> run(ctx, c -> audit(
                                                c, StringArgumentType.getString(c, "player"))))))
                        .then(Commands.literal("learn")
                                .then(Commands.argument("player", StringArgumentType.word())
                                        .suggests(SUGGEST_PLAYERS)
                                        .then(Commands.argument("list", StringArgumentType.word())
                                                .suggests(SUGGEST_ACTIVE)
                                                .executes(ctx -> run(ctx, c -> learn(
                                                        c,
                                                        StringArgumentType.getString(c, "player"),
                                                        StringArgumentType.getString(c, "list")))))))
                        // ---- 必需 Mod 管理
                        .then(requiredNode())
                        // ---- 白名单 / 黑名单
                        .then(whitelistNode())
                        .then(blacklistNode())
        );
    }

    private static LiteralArgumentBuilder<CommandSourceStack> exemptNode() {
        return Commands.literal("exempt")
                .then(Commands.literal("list").executes(ctx -> run(ctx, MacCommand::exemptList)))
                .then(Commands.literal("add")
                        .then(Commands.argument("player", StringArgumentType.word())
                                .suggests(SUGGEST_PLAYERS)
                                .executes(ctx -> run(ctx, c -> exemptAdd(
                                        c, StringArgumentType.getString(c, "player"))))))
                .then(Commands.literal("remove")
                        .then(Commands.argument("player", StringArgumentType.word())
                                .suggests(suggestIds(() -> cfg().getExemptPlayers()))
                                .executes(ctx -> run(ctx, c -> exemptRemove(
                                        c, StringArgumentType.getString(c, "player"))))));
    }

    /** 允许接入的本模组版本列表管理。 */
    private static LiteralArgumentBuilder<CommandSourceStack> allowedMacNode() {
        return Commands.literal("allowedmac")
                .then(Commands.literal("list").executes(ctx -> run(ctx, MacCommand::allowedMacList)))
                .then(Commands.literal("add")
                        .then(Commands.argument("version", StringArgumentType.word())
                                .executes(ctx -> run(ctx, c -> allowedMacAdd(
                                        c, StringArgumentType.getString(c, "version"))))))
                .then(Commands.literal("remove")
                        .then(Commands.argument("version", StringArgumentType.word())
                                .suggests(suggestIds(() -> cfg().getAllowedMacVersions()))
                                .executes(ctx -> run(ctx, c -> allowedMacRemove(
                                        c, StringArgumentType.getString(c, "version"))))));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> requiredNode() {
        return Commands.literal("required")
                .then(Commands.literal("list").executes(ctx -> run(ctx, MacCommand::requiredList)))
                .then(Commands.literal("add")
                        .then(Commands.argument("id", StringArgumentType.word())
                                .then(Commands.argument("spec", StringArgumentType.greedyString())
                                        .executes(ctx -> run(ctx, c -> requiredAdd(c,
                                                StringArgumentType.getString(c, "id"),
                                                StringArgumentType.getString(c, "spec")))))
                                .executes(ctx -> run(ctx, c -> requiredAdd(c,
                                        StringArgumentType.getString(c, "id"), "")))))
                .then(Commands.literal("remove")
                        .then(Commands.argument("id", StringArgumentType.word())
                                .suggests(suggestRequiredIds())
                                .executes(ctx -> run(ctx, c -> requiredRemove(c,
                                        StringArgumentType.getString(c, "id"))))));
    }

    /** 白名单管理：add 的 id 支持 {@code *} 通配符，可追加版本约束（如 {@code >=1.0}）。 */
    private static LiteralArgumentBuilder<CommandSourceStack> whitelistNode() {
        return policyNode("whitelist", true);
    }

    private static LiteralArgumentBuilder<CommandSourceStack> blacklistNode() {
        return policyNode("blacklist", false);
    }

    private static LiteralArgumentBuilder<CommandSourceStack> policyNode(String name, boolean whitelist) {
        return Commands.literal(name)
                .then(Commands.literal("list")
                        .executes(ctx -> run(ctx, c -> policyList(c, whitelist))))
                .then(Commands.literal("add")
                        .then(Commands.argument("id", StringArgumentType.word())
                                .executes(ctx -> run(ctx, c -> policyAdd(c, whitelist,
                                        StringArgumentType.getString(c, "id"), "")))
                                .then(Commands.argument("bounds", StringArgumentType.greedyString())
                                        .executes(ctx -> run(ctx, c -> policyAdd(c, whitelist,
                                                StringArgumentType.getString(c, "id"),
                                                StringArgumentType.getString(c, "bounds")))))))
                .then(Commands.literal("remove")
                        .then(Commands.argument("id", StringArgumentType.word())
                                .suggests(suggestPolicyIds(whitelist))
                                .executes(ctx -> run(ctx, c -> policyRemove(c, whitelist,
                                        StringArgumentType.getString(c, "id"))))));
    }

    // ------------------------------------------------------------------ 统一执行壳

    private interface Op {
        int run(CommandContext<CommandSourceStack> ctx) throws Exception;
    }

    private static int run(CommandContext<CommandSourceStack> ctx, Op op) {
        // 回显文案按命令执行者的客户端语言渲染（控制台/命令方块保持服务端语言）。
        return I18n.withLocale(sourceLanguage(ctx.getSource()), () -> {
            try {
                return op.run(ctx);
            } catch (Exception ex) {
                ctx.getSource().sendFailure(Component.literal(
                        CommandText.runFailure(String.valueOf(ex.getMessage()))));
                return 0;
            }
        });
    }

    /** 命令执行者若为玩家则返回其客户端语言；否则返回 null（用服务端语言）。 */
    private static String sourceLanguage(CommandSourceStack src) {
        net.minecraft.server.level.ServerPlayer p = src.getPlayer();
        // Fabric 原版无语言接口，改用客户端在握手阶段上报的语言（服务端会话中保存）。
        return p == null ? null : Holder.service().clientLanguage(p.getStringUUID());
    }

    // ------------------------------------------------------------------ 实现

    private static int help(CommandContext<CommandSourceStack> ctx, int page) {
        for (String line : HelpText.page(page)) {
            send(ctx, line);
        }
        return 1;
    }

    private static int status(CommandContext<CommandSourceStack> ctx) {
        List<String> lines = service().statusLines();
        for (String l : lines) {
            send(ctx, l);
        }
        return 1;
    }

    private static int recent(CommandContext<CommandSourceStack> ctx) {
        List<ViolationRecord> v = service().recentViolations();
        if (v.isEmpty()) {
            send(ctx, CommandText.recentEmpty());
            return 1;
        }
        send(ctx, CommandText.recentHeader(v.size()));
        int shown = 0;
        for (ViolationRecord r : v) {
            if (shown++ >= 20) {
                break;
            }
            send(ctx, "  - " + r.displayText());
        }
        return 1;
    }

    private static int check(CommandContext<CommandSourceStack> ctx, String name) {
        String s = service().sessionStatus(name);
        if (s == null) {
            ctx.getSource().sendFailure(Component.literal(CommandText.checkNotFound(name)));
            return 0;
        }
        send(ctx, s);
        return 1;
    }

    private static int audit(CommandContext<CommandSourceStack> ctx, String name) {
        List<String> lines = service().auditLines(name);
        if (lines.isEmpty()) {
            send(ctx, CommandText.auditEmpty(name));
            return 1;
        }
        send(ctx, CommandText.auditHeader(name));
        for (String l : lines) {
            send(ctx, "  - " + l);
        }
        return 1;
    }

    private static int learn(CommandContext<CommandSourceStack> ctx, String name, String list) {
        PolicyMode pm = PolicyMode.byKey(list);
        if (pm == PolicyMode.SWITCH) {
            return fail(CommandText.errLearnMode());
        }
        send(ctx, service().learn(name, pm == PolicyMode.WHITELIST));
        return 1;
    }

    private static int reload(CommandContext<CommandSourceStack> ctx) {
        service().configManager().load();
        send(ctx, CommandText.reload());
        return 1;
    }

    private static int save(CommandContext<CommandSourceStack> ctx) {
        service().configManager().save();
        send(ctx, CommandText.save());
        return 1;
    }

    private static int recheck(CommandContext<CommandSourceStack> ctx) {
        service().forceRecheckAll();
        send(ctx, CommandText.recheck());
        return 1;
    }

    private static int enabled(CommandContext<CommandSourceStack> ctx, boolean value) {
        update(cfg -> cfg.setEnabled(value));
        send(ctx, CommandText.enabled(value));
        return 1;
    }

    private static int dryrun(CommandContext<CommandSourceStack> ctx, boolean value) {
        update(cfg -> cfg.setDryRun(value));
        send(ctx, CommandText.dryrun(value));
        return 1;
    }

    private static int mode(CommandContext<CommandSourceStack> ctx, String value) {
        PolicyMode pm = PolicyMode.byKey(value);
        update(cfg -> cfg.getPolicy().setMode(pm.key()));
        send(ctx, CommandText.mode(pm.key(), pm == PolicyMode.SWITCH));
        return 1;
    }

    private static int active(CommandContext<CommandSourceStack> ctx, String value) {
        PolicyMode pm = PolicyMode.byKey(value);
        if (pm == PolicyMode.SWITCH) {
            return fail(CommandText.errActiveMode());
        }
        update(cfg -> cfg.getPolicy().setActiveMode(pm.key()));
        send(ctx, CommandText.active(pm.key()));
        return 1;
    }

    private static int exemptList(CommandContext<CommandSourceStack> ctx) {
        List<String> list = cfg().getExemptPlayers();
        boolean ops = cfg().isExemptOps();
        if (list.isEmpty() && !ops) {
            send(ctx, CommandText.exemptEmpty());
            return 1;
        }
        send(ctx, CommandText.exemptHeader(list.size(), ops));
        for (String s : list) {
            send(ctx, "  - " + s);
        }
        return 1;
    }

    private static int exemptAdd(CommandContext<CommandSourceStack> ctx, String player) {
        List<String> list = cfg().getExemptPlayers();
        if (list.stream().anyMatch(i -> i.equalsIgnoreCase(player))) {
            send(ctx, CommandText.exemptExists(player));
            return 1;
        }
        update(c -> c.getExemptPlayers().add(player));
        send(ctx, CommandText.exemptAdded(player));
        return 1;
    }

    private static int exemptRemove(CommandContext<CommandSourceStack> ctx, String player) {
        final String key = player;
        boolean had = cfg().getExemptPlayers().stream().anyMatch(i -> i.equalsIgnoreCase(key));
        if (!had) {
            return fail(CommandText.exemptNotFound(key));
        }
        update(c -> c.getExemptPlayers().removeIf(i -> i.equalsIgnoreCase(key)));
        send(ctx, CommandText.exemptRemoved(key));
        return 1;
    }

    private static int allowedMacList(CommandContext<CommandSourceStack> ctx) {
        List<String> list = cfg().getAllowedMacVersions();
        if (list.isEmpty()) {
            send(ctx, CommandText.allowedMacEmpty());
            return 1;
        }
        send(ctx, CommandText.allowedMacHeader(list.size()));
        for (String s : list) {
            send(ctx, "  - " + s);
        }
        return 1;
    }

    private static int allowedMacAdd(CommandContext<CommandSourceStack> ctx, String version) {
        List<String> list = cfg().getAllowedMacVersions();
        if (list.stream().anyMatch(v -> v.equalsIgnoreCase(version))) {
            send(ctx, CommandText.allowedMacExists(version));
            return 1;
        }
        if ("*".equals(version)) {
            update(c -> c.getAllowedMacVersions().clear());
            send(ctx, CommandText.allowedMacCleared());
            return 1;
        }
        update(c -> c.getAllowedMacVersions().add(version));
        send(ctx, CommandText.allowedMacAdded(version));
        return 1;
    }

    private static int allowedMacRemove(CommandContext<CommandSourceStack> ctx, String version) {
        final String key = version;
        boolean had = cfg().getAllowedMacVersions().stream().anyMatch(v -> v.equalsIgnoreCase(key));
        if (!had) {
            return fail(CommandText.allowedMacNotFound(key));
        }
        update(c -> c.getAllowedMacVersions().removeIf(v -> v.equalsIgnoreCase(key)));
        send(ctx, CommandText.allowedMacRemoved(key));
        return 1;
    }

    private static int requiredList(CommandContext<CommandSourceStack> ctx) {
        MacConfig cfg = cfg();
        if (cfg.getRequiredMods().isEmpty()) {
            send(ctx, CommandText.requiredEmpty(cfg.requiredMode().key()));
            return 1;
        }
        send(ctx, CommandText.requiredHeader(cfg.requiredMode().key()));
        for (RequiredModRule r : cfg.getRequiredMods()) {
            send(ctx, "  - " + r);
        }
        return 1;
    }

    private static int requiredAdd(CommandContext<CommandSourceStack> ctx, String id, String spec) {
        if (id.equalsIgnoreCase(Mac.MOD_ID)) {
            return fail(CommandText.errSelfRequired());
        }
        RequiredModRule rule = new RequiredModRule(id);
        rule.applySpec(spec);
        update(cfg -> cfg.getRequiredMods().removeIf(r -> r.getId().equalsIgnoreCase(id)));
        update(cfg -> cfg.getRequiredMods().add(rule));
        send(ctx, CommandText.requiredAdded(rule.toString()));
        return 1;
    }

    private static int requiredRemove(CommandContext<CommandSourceStack> ctx, String id) {
        final String key = id;
        boolean had = cfg().getRequiredMods().stream().anyMatch(r -> r.getId().equalsIgnoreCase(key));
        if (!had) {
            return fail(CommandText.requiredNotFound(key));
        }
        update(cfg -> cfg.getRequiredMods().removeIf(r -> r.getId().equalsIgnoreCase(key)));
        send(ctx, CommandText.requiredRemoved(key));
        return 1;
    }

    private static List<PolicyEntry> policy(MacConfig cfg, boolean whitelist) {
        return whitelist ? cfg.getPolicy().getWhitelist() : cfg.getPolicy().getBlacklist();
    }

    private static int policyList(CommandContext<CommandSourceStack> ctx, boolean whitelist) {
        List<PolicyEntry> list = policy(cfg(), whitelist);
        String name = CommandText.listName(whitelist);
        if (list.isEmpty()) {
            send(ctx, CommandText.listEmpty(name));
            return 1;
        }
        send(ctx, CommandText.listHeader(name, list.size()));
        for (PolicyEntry e : list) {
            send(ctx, "  - " + e);
        }
        return 1;
    }

    private static int policyAdd(CommandContext<CommandSourceStack> ctx, boolean whitelist,
                                 String id, String bounds) {
        List<PolicyEntry> list = policy(cfg(), whitelist);
        String name = CommandText.listName(whitelist);
        if (list.stream().anyMatch(e -> e.getId() != null && e.getId().equalsIgnoreCase(id))) {
            send(ctx, CommandText.listExists(id, name));
            return 1;
        }
        PolicyEntry entry = new PolicyEntry(id);
        if (bounds != null && !bounds.trim().isEmpty()) {
            entry.applySpec(bounds.trim());
        }
        entry.normalize();
        update(c -> policy(c, whitelist).add(entry));
        send(ctx, CommandText.listAdded(name, entry.toString()));
        return 1;
    }

    private static int policyRemove(CommandContext<CommandSourceStack> ctx, boolean whitelist, String id) {
        List<PolicyEntry> list = policy(cfg(), whitelist);
        String name = CommandText.listName(whitelist);
        boolean had = list.stream().anyMatch(e -> e.getId() != null && e.getId().equalsIgnoreCase(id));
        if (!had) {
            return fail(CommandText.listNotFound(name, id));
        }
        update(c -> policy(c, whitelist).removeIf(e -> e.getId() != null && e.getId().equalsIgnoreCase(id)));
        send(ctx, CommandText.listRemoved(name, id));
        return 1;
    }

    // ------------------------------------------------------------------ 工具

    private static int fail(String text) {
        throw new IllegalArgumentException(text);
    }

    private static MacConfig cfg() {
        return service().configManager().current();
    }

    private static MacService service() {
        return Holder.service();
    }

    private static void update(Consumer<MacConfig> change) {
        service().configManager().updateAndSave(change);
    }

    private static void send(CommandContext<CommandSourceStack> ctx, String text) {
        ctx.getSource().sendSuccess(() -> Component.literal(text).withStyle(ChatFormatting.GREEN), false);
    }
}
