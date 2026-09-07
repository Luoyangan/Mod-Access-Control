package mcyszl.top.mod_access_control.fabric.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import mcyszl.top.mod_access_control.core.Mac;
import mcyszl.top.mod_access_control.core.model.MacConfig;
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
 * <p>子命令：status / recent / check &lt;玩家&gt; / reload / save / recheck /
 * enabled &lt;true|false&gt; / mode &lt;whitelist|blacklist|switch&gt; /
 * active &lt;whitelist|blacklist&gt; / required list|add|remove /
 * whitelist list|add|remove / blacklist list|add|remove。</p>
 */
public final class MacCommand {

    private MacCommand() {
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
                                        .executes(ctx -> run(ctx, c -> check(
                                                c, StringArgumentType.getString(c, "player"))))))
                        // ---- 配置生命周期
                        .then(Commands.literal("reload").executes(ctx -> run(ctx, MacCommand::reload)))
                        .then(Commands.literal("save").executes(ctx -> run(ctx, MacCommand::save)))
                        .then(Commands.literal("recheck").executes(ctx -> run(ctx, MacCommand::recheck)))
                        // ---- 顶层开关 / 模式
                        .then(Commands.literal("enabled")
                                .then(Commands.argument("value", BoolArgumentType.bool())
                                        .executes(ctx -> run(ctx, c -> enabled(c,
                                                BoolArgumentType.getBool(c, "value"))))))
                        .then(Commands.literal("mode")
                                .then(Commands.argument("value", StringArgumentType.word())
                                        .executes(ctx -> run(ctx, c -> mode(c,
                                                StringArgumentType.getString(c, "value"))))))
                        .then(Commands.literal("active")
                                .then(Commands.argument("value", StringArgumentType.word())
                                        .executes(ctx -> run(ctx, c -> active(c,
                                                StringArgumentType.getString(c, "value"))))))
                        // ---- 必需 Mod 管理
                        .then(requiredNode())
                        // ---- 白名单
                        .then(Commands.literal("whitelist")
                                .then(Commands.literal("list").executes(ctx -> run(ctx, MacCommand::whiteList)))
                                .then(Commands.literal("add")
                                        .then(Commands.argument("id", StringArgumentType.word())
                                                .executes(ctx -> run(ctx, c -> whiteAdd(
                                                        c, StringArgumentType.getString(c, "id"))))))
                                .then(Commands.literal("remove")
                                        .then(Commands.argument("id", StringArgumentType.word())
                                                .executes(ctx -> run(ctx, c -> whiteRemove(
                                                        c, StringArgumentType.getString(c, "id")))))))
                        // ---- 黑名单
                        .then(Commands.literal("blacklist")
                                .then(Commands.literal("list").executes(ctx -> run(ctx, MacCommand::blackList)))
                                .then(Commands.literal("add")
                                        .then(Commands.argument("id", StringArgumentType.word())
                                                .executes(ctx -> run(ctx, c -> blackAdd(
                                                        c, StringArgumentType.getString(c, "id"))))))
                                .then(Commands.literal("remove")
                                        .then(Commands.argument("id", StringArgumentType.word())
                                                .executes(ctx -> run(ctx, c -> blackRemove(
                                                        c, StringArgumentType.getString(c, "id")))))))
        );
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
                                .executes(ctx -> run(ctx, c -> requiredRemove(c,
                                        StringArgumentType.getString(c, "id"))))));
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
            ctx.getSource().sendFailure(Component.literal("[MAC] active 只能为 whitelist 或 blacklist"));
            return 0;
        }
        update(cfg -> cfg.getPolicy().setActiveMode(pm.key()));
        send(ctx, "当前生效策略已设为: " + pm.key());
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
            ctx.getSource().sendFailure(Component.literal("[MAC] 不能把本模组加入必需清单。"));
            return 0;
        }
        RequiredModRule rule = new RequiredModRule(id);
        parseSpec(rule, spec);
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
        ctx.getSource().sendFailure(Component.literal("[MAC] 清单中不存在: " + key));
        return 0;
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

    private static void parseSpec(RequiredModRule rule, String spec) {
        if (spec == null || spec.isBlank()) {
            return;
        }
        String[] toks = spec.trim().split("\\s+");
        for (int i = 0; i < toks.length; i++) {
            String t = toks[i];
            if (i + 1 < toks.length) {
                if ("--min".equalsIgnoreCase(t)) {
                    rule.setMinVersion(toks[++i]);
                    continue;
                }
                if ("--max".equalsIgnoreCase(t)) {
                    rule.setMaxVersion(toks[++i]);
                    continue;
                }
                if ("--exact".equalsIgnoreCase(t)) {
                    rule.setExactVersion(toks[++i]);
                    continue;
                }
            }
            // 裸参数按 “min~max” / “=x.y.z” 兼容处理
            String v = t;
            if (v.startsWith("=")) {
                rule.setExactVersion(v.substring(1));
            } else if (v.contains("~")) {
                String[] range = v.split("~", 2);
                rule.setMinVersion(range[0].isBlank() ? null : range[0]);
                rule.setMaxVersion(range.length > 1 && !range[1].isBlank() ? range[1] : null);
            }
        }
    }

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
            ctx.getSource().sendFailure(Component.literal("[MAC] " + name + "中不存在: " + id));
            return 0;
        }
        update(apply);
        send(ctx, "已从" + name + "移除: " + id);
        return 1;
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
