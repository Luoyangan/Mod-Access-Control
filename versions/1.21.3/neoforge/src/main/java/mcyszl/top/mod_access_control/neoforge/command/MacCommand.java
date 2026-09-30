// SPDX-License-Identifier: Apache-2.0
// Copyright 2024-2026 mcyszl.top (Luoyangan)

package mcyszl.top.mod_access_control.neoforge.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import mcyszl.top.mod_access_control.core.Mac;
import mcyszl.top.mod_access_control.core.i18n.Lang;
import mcyszl.top.mod_access_control.core.model.MacConfig;
import mcyszl.top.mod_access_control.core.model.PolicyEntry;
import mcyszl.top.mod_access_control.core.model.PolicyMode;
import mcyszl.top.mod_access_control.core.model.RequiredModRule;
import mcyszl.top.mod_access_control.core.session.ViolationRecord;
import mcyszl.top.mod_access_control.core.service.MacService;
import mcyszl.top.mod_access_control.neoforge.Holder;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;

import java.util.Arrays;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * 管理员命令 /mac（权限等级 2）。
 *
 * <p>子命令：help [页码] / status / recent / check &lt;玩家&gt; / audit &lt;玩家&gt; /
 * learn &lt;玩家&gt; &lt;whitelist|blacklist|required&gt; / reload / save / recheck /
 * enabled &lt;true|false&gt; / dryrun &lt;true|false&gt; /
 * mode &lt;whitelist|blacklist|switch&gt; / active &lt;whitelist|blacklist&gt; /
 * exempt list|add|remove / allowedmac list|add|remove /
 * required list|add|remove / whitelist list|add|remove /
 * blacklist list|add|remove / lang &lt;zh_cn|en_us&gt;。</p>
 *
 * <p>枚举参数（mode/active/learn）与列表移除、玩家名参数均带 Brigadier 自动补全。</p>
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

    /** learn 目标候选：白名单 / 黑名单 / 必需清单。 */
    private static final SuggestionProvider<CommandSourceStack> SUGGEST_LEARN = (ctx, b) -> {
        b.suggest("whitelist");
        b.suggest("blacklist");
        b.suggest("required");
        return b.buildFuture();
    };

    private static final SuggestionProvider<CommandSourceStack> SUGGEST_LANGUAGE = (ctx, b) -> {
        b.suggest(Lang.ZH_CN);
        b.suggest(Lang.ZH_TW);
        b.suggest(Lang.EN_US);
        b.suggest(Lang.JA_JP);
        b.suggest(Lang.RU_RU);
        return b.buildFuture();
    };

    private static SuggestionProvider<CommandSourceStack> suggestIds(Supplier<List<String>> source) {
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

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(
                Commands.literal("mac")
                        .requires(src -> src.hasPermission(2))
                        .executes(ctx -> run(ctx, MacCommand::status))
                        // ---- 只读
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
                                                .suggests(SUGGEST_LEARN)
                                                .executes(ctx -> run(ctx, c -> learn(
                                                        c,
                                                        StringArgumentType.getString(c, "player"),
                                                        StringArgumentType.getString(c, "list")))))))
                        // ---- 必需 Mod 管理
                        .then(requiredNode())
                        // ---- 服务端语言设置
                        .then(Commands.literal("lang")
                                .then(Commands.argument("value", StringArgumentType.word())
                                        .suggests(SUGGEST_LANGUAGE)
                                        .executes(ctx -> run(ctx, c -> language(c,
                                                StringArgumentType.getString(c, "value"))))))
                        // ---- 白名单 / 黑名单
                        .then(whitelistNode())
                        .then(blacklistNode())
                        .then(helpNode())
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

    private static LiteralArgumentBuilder<CommandSourceStack> whitelistNode() {
        return Commands.literal("whitelist")
                .then(Commands.literal("list").executes(ctx -> run(ctx, MacCommand::whiteList)))
                .then(Commands.literal("add")
                        .then(Commands.argument("id", StringArgumentType.word())
                                .then(Commands.argument("spec", StringArgumentType.greedyString())
                                        .executes(ctx -> run(ctx, c -> whiteAdd(c,
                                                StringArgumentType.getString(c, "id"),
                                                StringArgumentType.getString(c, "spec")))))
                                .executes(ctx -> run(ctx, c -> whiteAdd(c,
                                        StringArgumentType.getString(c, "id"), "")))))
                .then(Commands.literal("remove")
                        .then(Commands.argument("id", StringArgumentType.word())
                                .suggests(suggestPolicyIds(true))
                                .executes(ctx -> run(ctx, c -> whiteRemove(
                                        c, StringArgumentType.getString(c, "id"))))));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> blacklistNode() {
        return Commands.literal("blacklist")
                .then(Commands.literal("list").executes(ctx -> run(ctx, MacCommand::blackList)))
                .then(Commands.literal("add")
                        .then(Commands.argument("id", StringArgumentType.word())
                                .then(Commands.argument("spec", StringArgumentType.greedyString())
                                        .executes(ctx -> run(ctx, c -> blackAdd(c,
                                                StringArgumentType.getString(c, "id"),
                                                StringArgumentType.getString(c, "spec")))))
                                .executes(ctx -> run(ctx, c -> blackAdd(c,
                                        StringArgumentType.getString(c, "id"), "")))))
                .then(Commands.literal("remove")
                        .then(Commands.argument("id", StringArgumentType.word())
                                .suggests(suggestPolicyIds(false))
                                .executes(ctx -> run(ctx, c -> blackRemove(
                                        c, StringArgumentType.getString(c, "id"))))));
    }

    /** 白/黑名单条目 id 补全（含 * 通配符条目）。 */
    private static SuggestionProvider<CommandSourceStack> suggestPolicyIds(boolean whitelist) {
        return suggestIds(() -> {
            List<PolicyEntry> list = whitelist
                    ? cfg().getPolicy().getWhitelist() : cfg().getPolicy().getBlacklist();
            List<String> ids = new java.util.ArrayList<>();
            for (PolicyEntry e : list) {
                ids.add(e.getId());
            }
            return ids;
        });
    }

    // ------------------------------------------------------------------ 分页帮助

    /** 分页帮助（按服务端语言）。 */
    private static List<String> helpLines() {
        return Arrays.asList(
                Lang.tr("/mac or /mac status - show current policy and session status"),
                Lang.tr("/mac recent - recent violations (up to 20 entries)"),
                Lang.tr("/mac check <player> - view a player's session status"),
                Lang.tr("/mac audit <player> - view a player's mod history"),
                Lang.tr("/mac learn <player> <whitelist|blacklist|required> - build lists from a player's mods"),
                Lang.tr("/mac reload - reload rules from the config file"),
                Lang.tr("/mac save - save current config to file"),
                Lang.tr("/mac recheck - re-check online players with latest rules"),
                Lang.tr("/mac enabled <true|false> - master switch"),
                Lang.tr("/mac dryrun <true|false> - dry-run switch (no kicks)"),
                Lang.tr("/mac mode <whitelist|blacklist|switch> - set policy mode"),
                Lang.tr("/mac active <whitelist|blacklist> - active policy in switch mode"),
                Lang.tr("/mac exempt list|add|remove <player> - manage exemptions"),
                Lang.tr("/mac allowedmac list|add|remove <version> - manage allowed MAC versions"),
                Lang.tr("/mac required list|add|remove - manage required mods (add supports version constraints)"),
                Lang.tr("/mac whitelist list|add|remove <id> [constraint] - manage whitelist (add supports * wildcard and version constraints)"),
                Lang.tr("/mac blacklist list|add|remove <id> [constraint] - manage blacklist (add supports * wildcard and version constraints)"),
                Lang.tr("/mac lang <zh_cn|en_us> - set server message language"));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> helpNode() {
        return Commands.literal("help")
                .executes(ctx -> run(ctx, c -> help(c, 1)))
                .then(Commands.argument("page", IntegerArgumentType.integer(1))
                        .executes(ctx -> run(ctx, c -> help(c,
                                IntegerArgumentType.getInteger(c, "page")))));
    }

    private static int help(CommandContext<CommandSourceStack> ctx, int page) {
        List<String> lines = helpLines();
        int perPage = 10;
        int pages = (lines.size() + perPage - 1) / perPage;
        if (page < 1) {
            page = 1;
        }
        if (page > pages) {
            page = pages;
        }
        send(ctx, Lang.tr("===== /mac help page ") + page + "/" + pages
                + Lang.tr(" ====="));
        for (int i = (page - 1) * perPage; i < page * perPage && i < lines.size(); i++) {
            send(ctx, lines.get(i));
        }
        if (page < pages) {
            send(ctx, Lang.tr("Next page: /mac help ") + (page + 1));
        }
        return 1;
    }

    // ------------------------------------------------------------------ 统一执行壳

    private interface Op {
        int run(CommandContext<CommandSourceStack> ctx) throws Exception;
    }

    private static int run(CommandContext<CommandSourceStack> ctx, Op op) {
        try {
            return op.run(ctx);
        } catch (Exception ex) {
            ctx.getSource().sendFailure(Component.literal(
                    Lang.tr("[MAC] Command failed: ") + ex.getMessage()));
            return 0;
        }
    }

    // ------------------------------------------------------------------ 实现

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
            send(ctx, Lang.tr("No violations recorded."));
            return 1;
        }
        send(ctx, Lang.tr("Recent violations (newest first, ") + v.size()
                + Lang.tr(" total):"));
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
            ctx.getSource().sendFailure(Component.literal(
                    Lang.tr("[MAC] Player not found: ") + name));
            return 0;
        }
        send(ctx, s);
        return 1;
    }

    private static int audit(CommandContext<CommandSourceStack> ctx, String name) {
        List<String> lines = service().auditLines(name);
        if (lines.isEmpty()) {
            send(ctx, Lang.tr("No mod history for {}.", name));
            return 1;
        }
        send(ctx, name + Lang.tr("'s mod history (newest first):"));
        for (String l : lines) {
            send(ctx, "  - " + l);
        }
        return 1;
    }

    private static int learn(CommandContext<CommandSourceStack> ctx, String name, String list) {
        if (list.equalsIgnoreCase("required")) {
            send(ctx, service().learnRequired(name));
            return 1;
        }
        PolicyMode pm = PolicyMode.byKey(list);
        if (pm == PolicyMode.SWITCH) {
            return fail(Lang.tr("learn only accepts whitelist or blacklist."));
        }
        send(ctx, service().learn(name, pm == PolicyMode.WHITELIST));
        return 1;
    }

    private static int reload(CommandContext<CommandSourceStack> ctx) {
        service().configManager().load();
        send(ctx, Lang.tr("Reloaded from config file. Run /mac recheck to re-check online players with the latest rules."));
        return 1;
    }

    private static int save(CommandContext<CommandSourceStack> ctx) {
        service().configManager().save();
        send(ctx, Lang.tr("Current config saved to file."));
        return 1;
    }

    private static int recheck(CommandContext<CommandSourceStack> ctx) {
        service().forceRecheckAll();
        send(ctx, Lang.tr("Full re-check of online players completed with current rules."));
        return 1;
    }

    private static int enabled(CommandContext<CommandSourceStack> ctx, boolean value) {
        update(cfg -> cfg.setEnabled(value));
        send(ctx, Lang.tr("Master switch set to: ") + value
                + Lang.tr(" (when off, clients are no longer forced through admission checks)"));
        return 1;
    }

    private static int dryrun(CommandContext<CommandSourceStack> ctx, boolean value) {
        update(cfg -> cfg.setDryRun(value));
        send(ctx, Lang.tr("Dry-run mode set to: ") + value
                + (value ? Lang.tr(" (violations are only logged, no one is kicked)") : ""));
        return 1;
    }

    private static int mode(CommandContext<CommandSourceStack> ctx, String value) {
        PolicyMode pm = PolicyMode.byKey(value);
        update(cfg -> cfg.getPolicy().setMode(pm.key()));
        String note = pm == PolicyMode.SWITCH
                ? Lang.tr(" Tip: set the active policy with /mac active <whitelist|blacklist>.")
                : "";
        send(ctx, Lang.tr("Policy mode set to: ") + pm.key() + note);
        return 1;
    }

    private static int active(CommandContext<CommandSourceStack> ctx, String value) {
        PolicyMode pm = PolicyMode.byKey(value);
        if (pm == PolicyMode.SWITCH) {
            return fail(Lang.tr("active only accepts whitelist or blacklist."));
        }
        update(cfg -> cfg.getPolicy().setActiveMode(pm.key()));
        send(ctx, Lang.tr("Active policy set to: ") + pm.key());
        return 1;
    }

    private static int language(CommandContext<CommandSourceStack> ctx, String value) {
        String key = Lang.normalize(value);
        update(cfg -> cfg.setLanguage(key));
        send(ctx, Lang.tr("Server language set to: ") + key);
        return 1;
    }

    private static int exemptList(CommandContext<CommandSourceStack> ctx) {
        List<String> list = cfg().getExemptPlayers();
        boolean ops = cfg().isExemptOps();
        if (list.isEmpty() && !ops) {
            send(ctx, Lang.tr("Exemption list is empty (OPs are not exempt either)."));
            return 1;
        }
        send(ctx, Lang.tr("Exemption config (") + list.size()
                + Lang.tr(" entries") + (ops ? Lang.tr(", OPs also exempt") : "")
                + Lang.tr("):"));
        for (String s : list) {
            send(ctx, "  - " + s);
        }
        return 1;
    }

    private static int exemptAdd(CommandContext<CommandSourceStack> ctx, String player) {
        List<String> list = cfg().getExemptPlayers();
        if (list.stream().anyMatch(i -> i.equalsIgnoreCase(player))) {
            send(ctx, player + Lang.tr(" is already exempt."));
            return 1;
        }
        update(c -> c.getExemptPlayers().add(player));
        send(ctx, Lang.tr("Added to exemptions: ") + player);
        return 1;
    }

    private static int exemptRemove(CommandContext<CommandSourceStack> ctx, String player) {
        final String key = player;
        boolean had = cfg().getExemptPlayers().stream().anyMatch(i -> i.equalsIgnoreCase(key));
        if (!had) {
            return fail(Lang.tr("Not in exemption list: ") + key);
        }
        update(c -> c.getExemptPlayers().removeIf(i -> i.equalsIgnoreCase(key)));
        send(ctx, Lang.tr("Removed from exemptions: ") + key);
        return 1;
    }

    private static int allowedMacList(CommandContext<CommandSourceStack> ctx) {
        List<String> list = cfg().getAllowedMacVersions();
        if (list.isEmpty()) {
            send(ctx, Lang.tr("Allowed MAC version list is empty (any version allowed)."));
            return 1;
        }
        send(ctx, Lang.tr("Allowed MAC versions ({}):", list.size()));
        for (String s : list) {
            send(ctx, "  - " + s);
        }
        return 1;
    }

    private static int allowedMacAdd(CommandContext<CommandSourceStack> ctx, String version) {
        List<String> list = cfg().getAllowedMacVersions();
        if (list.stream().anyMatch(v -> v.equalsIgnoreCase(version))) {
            send(ctx, version + Lang.tr(" is already in the allow list."));
            return 1;
        }
        if ("*".equals(version)) {
            update(c -> c.getAllowedMacVersions().clear());
            send(ctx, Lang.tr("Restrictions cleared; any MAC version is allowed."));
            return 1;
        }
        update(c -> c.getAllowedMacVersions().add(version));
        send(ctx, Lang.tr("Added to allowed versions: ") + version);
        return 1;
    }

    private static int allowedMacRemove(CommandContext<CommandSourceStack> ctx, String version) {
        final String key = version;
        boolean had = cfg().getAllowedMacVersions().stream().anyMatch(v -> v.equalsIgnoreCase(key));
        if (!had) {
            return fail(Lang.tr("Not in allow list: ") + key);
        }
        update(c -> c.getAllowedMacVersions().removeIf(v -> v.equalsIgnoreCase(key)));
        send(ctx, Lang.tr("Removed from allowed versions: ") + key);
        return 1;
    }

    private static int requiredList(CommandContext<CommandSourceStack> ctx) {
        MacConfig cfg = cfg();
        if (cfg.getRequiredMods().isEmpty()) {
            send(ctx, Lang.tr("Required mod list is empty (check mode: ")
                    + cfg.requiredMode().key() + Lang.tr(")."));
            return 1;
        }
        send(ctx, Lang.tr("Required mods (check mode: ")
                + cfg.requiredMode().key() + Lang.tr("):"));
        for (RequiredModRule r : cfg.getRequiredMods()) {
            send(ctx, "  - " + r.getId() + Lang.tr("  constraint: ") + r.constraintText());
        }
        return 1;
    }

    private static int requiredAdd(CommandContext<CommandSourceStack> ctx, String id, String spec) {
        if (id.equalsIgnoreCase(Mac.MOD_ID)) {
            return fail(Lang.tr("Cannot add this mod itself to the required list."));
        }
        RequiredModRule rule = new RequiredModRule(id);
        rule.applySpec(spec);
        update(cfg -> cfg.getRequiredMods().removeIf(r -> r.getId().equalsIgnoreCase(id)));
        update(cfg -> cfg.getRequiredMods().add(rule));
        send(ctx, Lang.tr("Added required mod: ") + rule.toString());
        return 1;
    }

    private static int requiredRemove(CommandContext<CommandSourceStack> ctx, String id) {
        final String key = id;
        boolean had = cfg().getRequiredMods().stream().anyMatch(r -> r.getId().equalsIgnoreCase(key));
        if (!had) {
            return fail(Lang.tr("Not in required list: ") + key);
        }
        update(cfg -> cfg.getRequiredMods().removeIf(r -> r.getId().equalsIgnoreCase(key)));
        send(ctx, Lang.tr("Removed required mod: ") + key);
        return 1;
    }

    private static int whiteList(CommandContext<CommandSourceStack> ctx) {
        return policyList(ctx, true);
    }

    private static int whiteAdd(CommandContext<CommandSourceStack> ctx, String id, String spec) {
        return policyAddResult(ctx, service().policyAdd(true, id, spec));
    }

    private static int whiteRemove(CommandContext<CommandSourceStack> ctx, String id) {
        return policyAddResult(ctx, service().policyRemove(true, id));
    }

    private static int blackList(CommandContext<CommandSourceStack> ctx) {
        return policyList(ctx, false);
    }

    private static int blackAdd(CommandContext<CommandSourceStack> ctx, String id, String spec) {
        return policyAddResult(ctx, service().policyAdd(false, id, spec));
    }

    private static int blackRemove(CommandContext<CommandSourceStack> ctx, String id) {
        return policyAddResult(ctx, service().policyRemove(false, id));
    }

    private static int policyList(CommandContext<CommandSourceStack> ctx, boolean whitelist) {
        List<String> lines = service().policyLines(whitelist);
        String name = whitelist ? Lang.tr("whitelist") : Lang.tr("blacklist");
        if (lines.isEmpty()) {
            send(ctx, name + Lang.tr(" is empty."));
            return 1;
        }
        send(ctx, name + Lang.tr(" (") + lines.size()
                + Lang.tr(" entries; add supports * wildcard and version constraints):"));
        for (String s : lines) {
            send(ctx, s);
        }
        return 1;
    }

    private static int policyAddResult(CommandContext<CommandSourceStack> ctx, String result) {
        if (result.startsWith("!")) {
            return fail(result.substring(1));
        }
        send(ctx, result);
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
