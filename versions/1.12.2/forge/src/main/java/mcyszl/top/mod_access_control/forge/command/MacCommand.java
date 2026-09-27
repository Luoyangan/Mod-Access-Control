// SPDX-License-Identifier: Apache-2.0
// Copyright 2024-2026 mcyszl.top (Luoyangan)

package mcyszl.top.mod_access_control.forge.command;

import mcyszl.top.mod_access_control.core.Mac;
import mcyszl.top.mod_access_control.core.model.MacConfig;
import mcyszl.top.mod_access_control.core.model.PolicyEntry;
import mcyszl.top.mod_access_control.core.model.PolicyMode;
import mcyszl.top.mod_access_control.core.model.RequiredModRule;
import mcyszl.top.mod_access_control.core.service.MacService;
import mcyszl.top.mod_access_control.core.session.ViolationRecord;
import mcyszl.top.mod_access_control.forge.Holder;
import net.minecraft.command.CommandBase;
import net.minecraft.command.CommandException;
import net.minecraft.command.ICommandSender;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.Style;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.util.text.TextFormatting;
import net.minecraftforge.fml.common.FMLCommonHandler;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * 管理员命令 /mac（权限等级 2）——1.12.2 pre-Brigadier 的 ICommand 实现。
 *
 * <p>子命令与 1.20.1 Brigadier 版保持一致：help [页码] / status / recent / check &lt;玩家&gt; /
 * audit &lt;玩家&gt; / learn &lt;玩家&gt; &lt;whitelist|blacklist|required&gt; / reload / save /
 * recheck / enabled &lt;true|false&gt; / dryrun &lt;true|false&gt; /
 * mode &lt;whitelist|blacklist|switch&gt; / active &lt;whitelist|blacklist&gt; /
 * exempt list|add|remove / allowedmac list|add|remove /
 * required list|add|remove / whitelist list|add|remove &lt;id&gt; [约束] /
 * blacklist list|add|remove &lt;id&gt; [约束]。</p>
 */
public final class MacCommand extends CommandBase {

    private static final List<String> ROOT_SUBS = Arrays.asList(
            "help", "status", "recent", "check", "audit", "learn", "reload", "save", "recheck",
            "enabled", "dryrun", "mode", "active", "exempt", "allowedmac",
            "required", "whitelist", "blacklist");
    private static final List<String> LIST_SUBS = Arrays.asList("list", "add", "remove");
    private static final List<String> BOOLS = Arrays.asList("true", "false");
    private static final List<String> MODES = Arrays.asList("whitelist", "blacklist", "switch");
    private static final List<String> ACTIVE_MODES = Arrays.asList("whitelist", "blacklist");
    private static final List<String> LEARN_MODES = Arrays.asList("whitelist", "blacklist", "required");

    @Override
    public String getName() {
        return "mac";
    }

    @Override
    public String getUsage(ICommandSender sender) {
        return "/mac <help|status|recent|check|audit|learn|reload|save|recheck|enabled|dryrun|mode|active|exempt|allowedmac|required|whitelist|blacklist> ...";
    }

    @Override
    public int getRequiredPermissionLevel() {
        return 2;
    }

    @Override
    public void execute(MinecraftServer server, ICommandSender sender, String[] args) throws CommandException {
        try {
            dispatch(sender, args);
        } catch (MacCmdException ex) {
            send(sender, ex.getMessage(), TextFormatting.RED);
        } catch (Exception ex) {
            send(sender, "[MAC] 命令执行失败: " + ex.getMessage(), TextFormatting.RED);
        }
    }

    private void dispatch(ICommandSender sender, String[] args) {
        String sub = args.length == 0 ? "status" : args[0].toLowerCase();
        if (sub.equals("help")) {
            sendHelp(sender, args);
            return;
        }
        if (sub.equals("status")) {
            for (String l : service().statusLines()) {
                send(sender, l, TextFormatting.GREEN);
            }
            return;
        }
        if (sub.equals("recent")) {
            List<ViolationRecord> v = service().recentViolations();
            if (v.isEmpty()) {
                send(sender, "暂无违规记录。", TextFormatting.GREEN);
                return;
            }
            send(sender, "最近违规记录（最新在前，共 " + v.size() + " 条）：", TextFormatting.GREEN);
            int shown = 0;
            for (ViolationRecord r : v) {
                if (shown++ >= 20) {
                    break;
                }
                send(sender, "  - " + r.displayText(), TextFormatting.GREEN);
            }
            return;
        }
        if (sub.equals("check")) {
            String name = argOrThrow(args, 1, "用法: /mac check <玩家>");
            String s = service().sessionStatus(name);
            if (s == null) {
                throw new MacCmdException("[MAC] 找不到玩家: " + name);
            }
            send(sender, s, TextFormatting.GREEN);
            return;
        }
        if (sub.equals("audit")) {
            String name = argOrThrow(args, 1, "用法: /mac audit <玩家>");
            List<String> lines = service().auditLines(name);
            if (lines.isEmpty()) {
                send(sender, "没有 " + name + " 的历史 Mod 记录。", TextFormatting.GREEN);
                return;
            }
            send(sender, name + " 的历史 Mod 记录（最新在前）：", TextFormatting.GREEN);
            for (String l : lines) {
                send(sender, "  - " + l, TextFormatting.GREEN);
            }
            return;
        }
        if (sub.equals("learn")) {
            String name = argOrThrow(args, 1, "用法: /mac learn <玩家> <whitelist|blacklist|required>");
            String list = argOrThrow(args, 2, "用法: /mac learn <玩家> <whitelist|blacklist|required>");
            if (list.equalsIgnoreCase("required")) {
                send(sender, service().learnRequired(name), TextFormatting.GREEN);
                return;
            }
            PolicyMode pm = PolicyMode.byKey(list);
            if (pm == PolicyMode.SWITCH) {
                throw new MacCmdException("learn 只能用于 whitelist 或 blacklist。");
            }
            send(sender, service().learn(name, pm == PolicyMode.WHITELIST), TextFormatting.GREEN);
            return;
        }
        if (sub.equals("reload")) {
            service().configManager().load();
            send(sender, "已从配置文件重新加载。若需用最新规则复检在线玩家，请执行 /mac recheck", TextFormatting.GREEN);
            return;
        }
        if (sub.equals("save")) {
            service().configManager().save();
            send(sender, "已保存当前配置到文件。", TextFormatting.GREEN);
            return;
        }
        if (sub.equals("recheck")) {
            service().forceRecheckAll();
            send(sender, "已按当前规则对在线玩家执行一次完整复检。", TextFormatting.GREEN);
            return;
        }
        if (sub.equals("enabled")) {
            boolean value = boolArg(args);
            update(cfg -> cfg.setEnabled(value));
            send(sender, "总开关已设为: " + value + "（若关闭，客户端将不再被强制校验）", TextFormatting.GREEN);
            return;
        }
        if (sub.equals("dryrun")) {
            boolean value = boolArg(args);
            update(cfg -> cfg.setDryRun(value));
            send(sender, "试运行模式已设为: " + value
                    + (value ? "（违规只记录，不实际踢出玩家）" : ""), TextFormatting.GREEN);
            return;
        }
        if (sub.equals("mode")) {
            String value = argOrThrow(args, 1, "用法: /mac mode <whitelist|blacklist|switch>");
            PolicyMode pm = PolicyMode.byKey(value);
            update(cfg -> cfg.getPolicy().setMode(pm.key()));
            String note = pm == PolicyMode.SWITCH
                    ? " 提示：当前生效策略请用 /mac active <whitelist|blacklist> 指定。"
                    : "";
            send(sender, "策略模式已设为: " + pm.key() + note, TextFormatting.GREEN);
            return;
        }
        if (sub.equals("active")) {
            String value = argOrThrow(args, 1, "用法: /mac active <whitelist|blacklist>");
            PolicyMode pm = PolicyMode.byKey(value);
            if (pm == PolicyMode.SWITCH) {
                throw new MacCmdException("active 只能为 whitelist 或 blacklist。");
            }
            update(cfg -> cfg.getPolicy().setActiveMode(pm.key()));
            send(sender, "当前生效策略已设为: " + pm.key(), TextFormatting.GREEN);
            return;
        }
        if (sub.equals("exempt")) {
            listOp(sender, args, "豁免名单", new ListOp() {
                @Override
                public List<String> list() {
                    return cfg().getExemptPlayers();
                }

                @Override
                public void add(String id) {
                    update(c -> c.getExemptPlayers().add(id));
                }

                @Override
                public void remove(String id) {
                    update(c -> c.getExemptPlayers().removeIf(i -> i.equalsIgnoreCase(id)));
                }
            });
            return;
        }
        if (sub.equals("allowedmac")) {
            listOp(sender, args, "允许版本列表", new ListOp() {
                @Override
                public List<String> list() {
                    return cfg().getAllowedMacVersions();
                }

                @Override
                public void add(String id) {
                    if ("*".equals(id)) {
                        update(c -> c.getAllowedMacVersions().clear());
                        return;
                    }
                    update(c -> c.getAllowedMacVersions().add(id));
                }

                @Override
                public void remove(String id) {
                    update(c -> c.getAllowedMacVersions().removeIf(v -> v.equalsIgnoreCase(id)));
                }
            });
            return;
        }
        if (sub.equals("required")) {
            if (args.length >= 2 && "list".equalsIgnoreCase(args[1])) {
                MacConfig c = cfg();
                if (c.getRequiredMods().isEmpty()) {
                    send(sender, "必需 Mod 清单为空（校验模式: " + c.requiredMode().key() + "）。", TextFormatting.GREEN);
                    return;
                }
                send(sender, "必需 Mod（校验模式: " + c.requiredMode().key() + "）：", TextFormatting.GREEN);
                for (RequiredModRule r : c.getRequiredMods()) {
                    send(sender, "  - " + r.getId() + "  约束: " + r.constraintText(), TextFormatting.GREEN);
                }
                return;
            }
            if (args.length >= 3 && "add".equalsIgnoreCase(args[1])) {
                String id = args[2];
                if (id.equalsIgnoreCase(Mac.MOD_ID)) {
                    throw new MacCmdException("不能把本模组加入必需清单。");
                }
                StringBuilder spec = new StringBuilder();
                for (int i = 3; i < args.length; i++) {
                    if (spec.length() > 0) {
                        spec.append(' ');
                    }
                    spec.append(args[i]);
                }
                RequiredModRule rule = new RequiredModRule(id);
                rule.applySpec(spec.toString());
                update(cfg -> cfg.getRequiredMods().removeIf(r -> r.getId().equalsIgnoreCase(id)));
                update(cfg -> cfg.getRequiredMods().add(rule));
                send(sender, "已添加必需 Mod: " + rule.toString(), TextFormatting.GREEN);
                return;
            }
            if (args.length >= 3 && "remove".equalsIgnoreCase(args[1])) {
                String id = args[2];
                boolean had = cfg().getRequiredMods().stream().anyMatch(r -> r.getId().equalsIgnoreCase(id));
                if (!had) {
                    throw new MacCmdException("清单中不存在: " + id);
                }
                update(cfg -> cfg.getRequiredMods().removeIf(r -> r.getId().equalsIgnoreCase(id)));
                send(sender, "已移除必需 Mod: " + id, TextFormatting.GREEN);
                return;
            }
            throw new MacCmdException("用法: /mac required list | add <id> [版本约束] | remove <id>");
        }
        if (sub.equals("whitelist")) {
            policyOp(sender, args, true);
            return;
        }
        if (sub.equals("blacklist")) {
            policyOp(sender, args, false);
            return;
        }
        throw new MacCmdException("未知子命令: " + sub + "。" + getUsage(sender));
    }

    // ------------------------------------------------------------------ 分页帮助

    private static final List<String> HELP_LINES = Arrays.asList(
            "/mac 或 /mac status - 查看当前策略与会话状态",
            "/mac recent - 最近违规记录（最多展示 20 条）",
            "/mac check <玩家> - 查看玩家会话状态",
            "/mac audit <玩家> - 查看玩家历史 Mod 记录",
            "/mac learn <玩家> <whitelist|blacklist|required> - 用玩家 Mod 清单一键建名单",
            "/mac reload - 从配置文件重新加载规则",
            "/mac save - 保存当前配置到文件",
            "/mac recheck - 用最新规则对在线玩家复检",
            "/mac enabled <true|false> - 总开关",
            "/mac dryrun <true|false> - 试运行开关（违规不踢出）",
            "/mac mode <whitelist|blacklist|switch> - 设置策略模式",
            "/mac active <whitelist|blacklist> - 设置 switch 模式生效策略",
            "/mac exempt list|add|remove <玩家> - 管理豁免名单",
            "/mac allowedmac list|add|remove <版本> - 管理允许接入的本模组版本",
            "/mac required list|add|remove - 管理必需 Mod（add 支持版本约束）",
            "/mac whitelist list|add|remove <id> [约束] - 管理白名单（add 支持 * 通配符与版本约束）",
            "/mac blacklist list|add|remove <id> [约束] - 管理黑名单（add 支持 * 通配符与版本约束）");

    private void sendHelp(ICommandSender sender, String[] args) {
        int perPage = 10;
        int pages = (HELP_LINES.size() + perPage - 1) / perPage;
        int page = 1;
        if (args.length >= 2) {
            try {
                page = Integer.parseInt(args[1]);
            } catch (NumberFormatException ignored) {
            }
        }
        if (page < 1) {
            page = 1;
        }
        if (page > pages) {
            page = pages;
        }
        send(sender, "===== /mac 帮助 第 " + page + "/" + pages + " 页 =====", TextFormatting.GREEN);
        for (int i = (page - 1) * perPage; i < page * perPage && i < HELP_LINES.size(); i++) {
            send(sender, HELP_LINES.get(i), TextFormatting.GREEN);
        }
        if (page < pages) {
            send(sender, "下一页: /mac help " + (page + 1), TextFormatting.GREEN);
        }
    }

    // ------------------------------------------------------------------ 列表型子命令公共壳

    private interface ListOp {
        List<String> list();

        void add(String id);

        void remove(String id);
    }

    private void listOp(ICommandSender sender, String[] args, String name, ListOp op) {
        if (args.length >= 2 && "list".equalsIgnoreCase(args[1])) {
            List<String> ids = op.list();
            if (ids.isEmpty()) {
                send(sender, name + "为空。", TextFormatting.GREEN);
                return;
            }
            send(sender, name + "（" + ids.size() + " 项）：", TextFormatting.GREEN);
            for (String s : ids) {
                send(sender, "  - " + s, TextFormatting.GREEN);
            }
            return;
        }
        if (args.length >= 3 && "add".equalsIgnoreCase(args[1])) {
            String id = args[2];
            if (op.list().stream().anyMatch(i -> i.equalsIgnoreCase(id))) {
                send(sender, id + " 已存在于" + name + "。", TextFormatting.GREEN);
                return;
            }
            op.add(id);
            send(sender, "已向" + name + "加入: " + id, TextFormatting.GREEN);
            return;
        }
        if (args.length >= 3 && "remove".equalsIgnoreCase(args[1])) {
            String id = args[2];
            boolean had = op.list().stream().anyMatch(i -> i.equalsIgnoreCase(id));
            if (!had) {
                throw new MacCmdException(name + "中不存在: " + id);
            }
            op.remove(id);
            send(sender, "已从" + name + "移除: " + id, TextFormatting.GREEN);
            return;
        }
        throw new MacCmdException("用法: /mac " + args[0] + " list | add <id> | remove <id>");
    }

    // ------------------------------------------------------------------ 白/黑名单（PolicyEntry，支持 * 通配符与版本约束）

    private void policyOp(ICommandSender sender, String[] args, boolean whitelist) {
        String name = whitelist ? "白名单" : "黑名单";
        if (args.length >= 2 && "list".equalsIgnoreCase(args[1])) {
            List<String> lines = service().policyLines(whitelist);
            if (lines.isEmpty()) {
                send(sender, name + "为空。", TextFormatting.GREEN);
                return;
            }
            send(sender, name + "（" + lines.size() + " 项，add 支持 * 通配符与版本约束）：", TextFormatting.GREEN);
            for (String l : lines) {
                send(sender, l, TextFormatting.GREEN);
            }
            return;
        }
        if (args.length >= 3 && "add".equalsIgnoreCase(args[1])) {
            String id = args[2];
            StringBuilder spec = new StringBuilder();
            for (int i = 3; i < args.length; i++) {
                if (spec.length() > 0) {
                    spec.append(' ');
                }
                spec.append(args[i]);
            }
            policyResult(sender, service().policyAdd(whitelist, id, spec.toString()));
            return;
        }
        if (args.length >= 3 && "remove".equalsIgnoreCase(args[1])) {
            policyResult(sender, service().policyRemove(whitelist, args[2]));
            return;
        }
        throw new MacCmdException("用法: /mac " + args[0] + " list | add <id> [约束] | remove <id>");
    }

    /** service 回显统一处理："!" 开头为失败提示（红色），其余为成功回显。 */
    private void policyResult(ICommandSender sender, String result) {
        if (result.startsWith("!")) {
            throw new MacCmdException(result.substring(1));
        }
        send(sender, result, TextFormatting.GREEN);
    }

    // ------------------------------------------------------------------ 工具

    private static final class MacCmdException extends RuntimeException {
        MacCmdException(String message) {
            super(message);
        }
    }

    private static String argOrThrow(String[] args, int index, String usage) {
        if (args.length <= index || args[index] == null || args[index].isEmpty()) {
            throw new MacCmdException(usage);
        }
        return args[index];
    }

    private static boolean boolArg(String[] args) {
        String v = argOrThrow(args, 1, "用法: 需要 <true|false> 参数");
        if ("true".equalsIgnoreCase(v)) {
            return true;
        }
        if ("false".equalsIgnoreCase(v)) {
            return false;
        }
        throw new MacCmdException("参数必须是 true 或 false: " + v);
    }

    private static MacConfig cfg() {
        return service().configManager().current();
    }

    private static MacService service() {
        return Holder.service();
    }

    private static void update(java.util.function.Consumer<MacConfig> change) {
        service().configManager().updateAndSave(change);
    }

    private static void send(ICommandSender sender, String text, TextFormatting color) {
        TextComponentString c = new TextComponentString(text);
        Style style = c.getStyle();
        style.setColor(color);
        sender.sendMessage(c);
    }

    // ------------------------------------------------------------------ Tab 补全

    @Override
    public List<String> getTabCompletions(MinecraftServer server, ICommandSender sender, String[] args, @Nullable BlockPos pos) {
        if (args.length == 1) {
            return getListOfStringsMatchingLastWord(args, ROOT_SUBS);
        }
        String sub = args[0].toLowerCase();
        if (args.length == 2) {
            if (sub.equals("help")) {
                return getListOfStringsMatchingLastWord(args, Arrays.asList("1", "2"));
            }
            if (sub.equals("check") || sub.equals("audit") || sub.equals("learn")) {
                return playerNames(server, args);
            }
            if (sub.equals("enabled") || sub.equals("dryrun")) {
                return getListOfStringsMatchingLastWord(args, BOOLS);
            }
            if (sub.equals("mode")) {
                return getListOfStringsMatchingLastWord(args, MODES);
            }
            if (sub.equals("active")) {
                return getListOfStringsMatchingLastWord(args, ACTIVE_MODES);
            }
            if (sub.equals("exempt") || sub.equals("allowedmac")
                    || sub.equals("required") || sub.equals("whitelist") || sub.equals("blacklist")) {
                return getListOfStringsMatchingLastWord(args, LIST_SUBS);
            }
        }
        if (args.length == 3) {
            if (sub.equals("learn")) {
                return getListOfStringsMatchingLastWord(args, LEARN_MODES);
            }
            if (sub.equals("exempt") && "add".equalsIgnoreCase(args[1])) {
                return playerNames(server, args);
            }
            if (sub.equals("exempt") && "remove".equalsIgnoreCase(args[1])) {
                return getListOfStringsMatchingLastWord(args, cfg().getExemptPlayers());
            }
            if (sub.equals("allowedmac") && "remove".equalsIgnoreCase(args[1])) {
                return getListOfStringsMatchingLastWord(args, cfg().getAllowedMacVersions());
            }
            if (sub.equals("required") && "remove".equalsIgnoreCase(args[1])) {
                return getListOfStringsMatchingLastWord(args, requiredIds());
            }
            if ((sub.equals("whitelist") || sub.equals("blacklist")) && "remove".equalsIgnoreCase(args[1])) {
                List<PolicyEntry> src = sub.equals("whitelist")
                        ? cfg().getPolicy().getWhitelist() : cfg().getPolicy().getBlacklist();
                List<String> ids = new ArrayList<>();
                for (PolicyEntry e : src) {
                    ids.add(e.getId());
                }
                return getListOfStringsMatchingLastWord(args, ids);
            }
        }
        return Collections.emptyList();
    }

    private static List<String> requiredIds() {
        List<String> ids = new ArrayList<>();
        for (RequiredModRule r : cfg().getRequiredMods()) {
            ids.add(r.getId());
        }
        return ids;
    }

    private static List<String> playerNames(MinecraftServer server, String[] args) {
        List<String> names = new ArrayList<>();
        if (server == null) {
            server = FMLCommonHandler.instance().getMinecraftServerInstance();
        }
        if (server != null) {
            for (net.minecraft.entity.player.EntityPlayerMP p : server.getPlayerList().getPlayers()) {
                names.add(p.getGameProfile().getName());
            }
        }
        return getListOfStringsMatchingLastWord(args, names);
    }
}
