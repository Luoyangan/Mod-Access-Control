// SPDX-License-Identifier: Apache-2.0
// Copyright 2024-2026 mcyszl.top (Luoyangan)

package mcyszl.top.mod_access_control.neoforge.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import mcyszl.top.mod_access_control.core.Mac;
import mcyszl.top.mod_access_control.core.model.MacConfig;
import mcyszl.top.mod_access_control.core.model.PolicyMode;
import mcyszl.top.mod_access_control.core.model.RequiredModRule;
import mcyszl.top.mod_access_control.core.session.ViolationRecord;
import mcyszl.top.mod_access_control.core.service.MacService;
import mcyszl.top.mod_access_control.neoforge.Holder;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;

import java.util.List;
import java.util.function.Consumer;

/**
 * 管理员命令 /mac（权限等级 2）。
 *
 * <p>子命令：status / recent / check &lt;玩家&gt; / audit &lt;玩家&gt; /
 * learn &lt;玩家&gt; &lt;whitelist|blacklist&gt; / reload / save / recheck /
 * enabled &lt;true|false&gt; / dryrun &lt;true|false&gt; /
 * mode &lt;whitelist|blacklist|switch&gt; / active &lt;whitelist|blacklist&gt; /
 * exempt list|add|remove / required list|add|remove /
 * whitelist list|add|remove / blacklist list|add|remove。</p>
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

    private static SuggestionProvider<CommandSourceStack> suggestIds(List<String> source) {
        return (ctx, b) -> {
            if (source != null) {
                for (String s : source) {
                    b.suggest(s);
                }
            }
            return b.buildFuture();
        };
    }

    private static SuggestionProvider<CommandSourceStack> suggestRequiredIds() {
        return suggestIds(cfg().getRequiredMods().stream().map(RequiredModRule::getId).toList());
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
                                .suggests(suggestIds(cfg().getExemptPlayers()))
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
                                .suggests(suggestIds(cfg().getAllowedMacVersions()))
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
                                .executes(ctx -> run(ctx, c -> whiteAdd(
                                        c, StringArgumentType.getString(c, "id"))))))
                .then(Commands.literal("remove")
                        .then(Commands.argument("id", StringArgumentType.word())
                                .suggests(suggestIds(cfg().getPolicy().getWhitelist()))
                                .executes(ctx -> run(ctx, c -> whiteRemove(
                                        c, StringArgumentType.getString(c, "id"))))));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> blacklistNode() {
        return Commands.literal("blacklist")
                .then(Commands.literal("list").executes(ctx -> run(ctx, MacCommand::blackList)))
                .then(Commands.literal("add")
                        .then(Commands.argument("id", StringArgumentType.word())
                                .executes(ctx -> run(ctx, c -> blackAdd(
                                        c, StringArgumentType.getString(c, "id"))))))
                .then(Commands.literal("remove")
                        .then(Commands.argument("id", StringArgumentType.word())
                                .suggests(suggestIds(cfg().getPolicy().getBlacklist()))
                                .executes(ctx -> run(ctx, c -> blackRemove(
                                        c, StringArgumentType.getString(c, "id"))))));
    }

    // ------------------------------------------------------------------ 统一执行壳

    private interface Op {
        int run(CommandContext<CommandSourceStack> ctx) throws Exception;
    }

    private static int run(CommandContext<CommandSourceStack> ctx, Op op) {
        try {
            return op.run(ctx);
        } catch (Exception ex) {
            ctx.getSource().sendFailure(Component.literal("[MAC] 命令执行失败: " + ex.getMessage()));
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
            send(ctx, "暂无违规记录。");
            return 1;
        }
        send(ctx, "最近违规记录（最新在前，共 " + v.size() + " 条）：");
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
            ctx.getSource().sendFailure(Component.literal("[MAC] 找不到玩家: " + name));
            return 0;
        }
        send(ctx, s);
        return 1;
    }

    private static int audit(CommandContext<CommandSourceStack> ctx, String name) {
        List<String> lines = service().auditLines(name);
        if (lines.isEmpty()) {
            send(ctx, "没有 " + name + " 的历史 Mod 记录。");
            return 1;
        }
        send(ctx, name + " 的历史 Mod 记录（最新在前）：");
        for (String l : lines) {
            send(ctx, "  - " + l);
        }
        return 1;
    }

    private static int learn(CommandContext<CommandSourceStack> ctx, String name, String list) {
        PolicyMode pm = PolicyMode.byKey(list);
        if (pm == PolicyMode.SWITCH) {
            return fail("learn 只能用于 whitelist 或 blacklist。");
        }
        send(ctx, service().learn(name, pm == PolicyMode.WHITELIST));
        return 1;
    }

    private static int reload(CommandContext<CommandSourceStack> ctx) {
        service().configManager().load();
        send(ctx, "已从配置文件重新加载。若需用最新规则复检在线玩家，请执行 /mac recheck");
        return 1;
    }

    private static int save(CommandContext<CommandSourceStack> ctx) {
        service().configManager().save();
        send(ctx, "已保存当前配置到文件。");
        return 1;
    }

    private static int recheck(CommandContext<CommandSourceStack> ctx) {
        service().forceRecheckAll();
        send(ctx, "已按当前规则对在线玩家执行一次完整复检。");
        return 1;
    }

    private static int enabled(CommandContext<CommandSourceStack> ctx, boolean value) {
        update(cfg -> cfg.setEnabled(value));
        send(ctx, "总开关已设为: " + value + "（若关闭，客户端将不再被强制校验）");
        return 1;
    }

    private static int dryrun(CommandContext<CommandSourceStack> ctx, boolean value) {
        update(cfg -> cfg.setDryRun(value));
        send(ctx, "试运行模式已设为: " + value
                + (value ? "（违规只记录，不实际踢出玩家）" : ""));
        return 1;
    }

    private static int mode(CommandContext<CommandSourceStack> ctx, String value) {
        PolicyMode pm = PolicyMode.byKey(value);
        update(cfg -> cfg.getPolicy().setMode(pm.key()));
        String note = pm == PolicyMode.SWITCH
                ? " 提示：当前生效策略请用 /mac active <whitelist|blacklist> 指定。"
                : "";
        send(ctx, "策略模式已设为: " + pm.key() + note);
        return 1;
    }

    private static int active(CommandContext<CommandSourceStack> ctx, String value) {
        PolicyMode pm = PolicyMode.byKey(value);
        if (pm == PolicyMode.SWITCH) {
            return fail("active 只能为 whitelist 或 blacklist。");
        }
        update(cfg -> cfg.getPolicy().setActiveMode(pm.key()));
        send(ctx, "当前生效策略已设为: " + pm.key());
        return 1;
    }

    private static int exemptList(CommandContext<CommandSourceStack> ctx) {
        List<String> list = cfg().getExemptPlayers();
        boolean ops = cfg().isExemptOps();
        if (list.isEmpty() && !ops) {
            send(ctx, "豁免名单为空（且未豁免 OP）。");
            return 1;
        }
        send(ctx, "豁免配置（" + list.size() + " 条" + (ops ? "，同时豁免 OP" : "") + "）：");
        for (String s : list) {
            send(ctx, "  - " + s);
        }
        return 1;
    }

    private static int exemptAdd(CommandContext<CommandSourceStack> ctx, String player) {
        List<String> list = cfg().getExemptPlayers();
        if (list.stream().anyMatch(i -> i.equalsIgnoreCase(player))) {
            send(ctx, player + " 已在豁免名单中。");
            return 1;
        }
        update(c -> c.getExemptPlayers().add(player));
        send(ctx, "已加入豁免名单: " + player);
        return 1;
    }

    private static int exemptRemove(CommandContext<CommandSourceStack> ctx, String player) {
        final String key = player;
        boolean had = cfg().getExemptPlayers().stream().anyMatch(i -> i.equalsIgnoreCase(key));
        if (!had) {
            return fail("豁免名单中不存在: " + key);
        }
        update(c -> c.getExemptPlayers().removeIf(i -> i.equalsIgnoreCase(key)));
        send(ctx, "已移出豁免名单: " + key);
        return 1;
    }

    private static int allowedMacList(CommandContext<CommandSourceStack> ctx) {
        List<String> list = cfg().getAllowedMacVersions();
        if (list.isEmpty()) {
            send(ctx, "允许的本模组版本列表为空（放行任意版本）。");
            return 1;
        }
        send(ctx, "允许接入的本模组版本（" + list.size() + " 项）：");
        for (String s : list) {
            send(ctx, "  - " + s);
        }
        return 1;
    }

    private static int allowedMacAdd(CommandContext<CommandSourceStack> ctx, String version) {
        List<String> list = cfg().getAllowedMacVersions();
        if (list.stream().anyMatch(v -> v.equalsIgnoreCase(version))) {
            send(ctx, version + " 已在允许列表中。");
            return 1;
        }
        if ("*".equals(version)) {
            update(c -> c.getAllowedMacVersions().clear());
            send(ctx, "已清空限制，放行任意本模组版本。");
            return 1;
        }
        update(c -> c.getAllowedMacVersions().add(version));
        send(ctx, "已加入允许版本: " + version);
        return 1;
    }

    private static int allowedMacRemove(CommandContext<CommandSourceStack> ctx, String version) {
        final String key = version;
        boolean had = cfg().getAllowedMacVersions().stream().anyMatch(v -> v.equalsIgnoreCase(key));
        if (!had) {
            return fail("允许列表中不存在: " + key);
        }
        update(c -> c.getAllowedMacVersions().removeIf(v -> v.equalsIgnoreCase(key)));
        send(ctx, "已移出允许版本: " + key);
        return 1;
    }

    private static int requiredList(CommandContext<CommandSourceStack> ctx) {
        MacConfig cfg = cfg();
        if (cfg.getRequiredMods().isEmpty()) {
            send(ctx, "必需 Mod 清单为空（校验模式: " + cfg.requiredMode().key() + "）。");
            return 1;
        }
        send(ctx, "必需 Mod（校验模式: " + cfg.requiredMode().key() + "）：");
        for (RequiredModRule r : cfg.getRequiredMods()) {
            send(ctx, "  - " + r.getId() + "  约束: " + r.constraintText());
        }
        return 1;
    }

    private static int requiredAdd(CommandContext<CommandSourceStack> ctx, String id, String spec) {
        if (id.equalsIgnoreCase(Mac.MOD_ID)) {
            return fail("不能把本模组加入必需清单。");
        }
        RequiredModRule rule = new RequiredModRule(id);
        rule.applySpec(spec);
        update(cfg -> cfg.getRequiredMods().removeIf(r -> r.getId().equalsIgnoreCase(id)));
        update(cfg -> cfg.getRequiredMods().add(rule));
        send(ctx, "已添加必需 Mod: " + rule.toString());
        return 1;
    }

    private static int requiredRemove(CommandContext<CommandSourceStack> ctx, String id) {
        final String key = id;
        boolean removed = cfg().getRequiredMods().removeIf(r -> r.getId().equalsIgnoreCase(key));
        if (removed) {
            update(cfg -> cfg.getRequiredMods().removeIf(r -> r.getId().equalsIgnoreCase(key)));
            send(ctx, "已移除必需 Mod: " + key);
            return 1;
        }
        return fail("清单中不存在: " + key);
    }

    private static int whiteList(CommandContext<CommandSourceStack> ctx) {
        return listIds(ctx, "白名单", cfg().getPolicy().getWhitelist());
    }

    private static int whiteAdd(CommandContext<CommandSourceStack> ctx, String id) {
        return addId(ctx, "白名单", cfg().getPolicy().getWhitelist(), id, c -> c.getPolicy().getWhitelist().add(id));
    }

    private static int whiteRemove(CommandContext<CommandSourceStack> ctx, String id) {
        return removeId(ctx, "白名单", cfg().getPolicy().getWhitelist(), id, c -> c.getPolicy().getWhitelist().removeIf(i -> i.equalsIgnoreCase(id)));
    }

    private static int blackList(CommandContext<CommandSourceStack> ctx) {
        return listIds(ctx, "黑名单", cfg().getPolicy().getBlacklist());
    }

    private static int blackAdd(CommandContext<CommandSourceStack> ctx, String id) {
        return addId(ctx, "黑名单", cfg().getPolicy().getBlacklist(), id, c -> c.getPolicy().getBlacklist().add(id));
    }

    private static int blackRemove(CommandContext<CommandSourceStack> ctx, String id) {
        return removeId(ctx, "黑名单", cfg().getPolicy().getBlacklist(), id, c -> c.getPolicy().getBlacklist().removeIf(i -> i.equalsIgnoreCase(id)));
    }

    // ------------------------------------------------------------------ 工具

    private static int listIds(CommandContext<CommandSourceStack> ctx, String name, List<String> ids) {
        if (ids.isEmpty()) {
            send(ctx, name + "为空。");
            return 1;
        }
        send(ctx, name + "（" + ids.size() + " 项）：");
        for (String s : ids) {
            send(ctx, "  - " + s);
        }
        return 1;
    }

    private static int addId(CommandContext<CommandSourceStack> ctx, String name, List<String> cur,
                             String id, Consumer<MacConfig> apply) {
        if (cur.stream().anyMatch(i -> i.equalsIgnoreCase(id))) {
            send(ctx, id + " 已存在于" + name + "。");
            return 1;
        }
        update(apply);
        send(ctx, "已向" + name + "加入: " + id);
        return 1;
    }

    private static int removeId(CommandContext<CommandSourceStack> ctx, String name, List<String> cur,
                                String id, Consumer<MacConfig> apply) {
        boolean had = cur.stream().anyMatch(i -> i.equalsIgnoreCase(id));
        if (!had) {
            return fail(name + "中不存在: " + id);
        }
        update(apply);
        send(ctx, "已从" + name + "移除: " + id);
        return 1;
    }

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