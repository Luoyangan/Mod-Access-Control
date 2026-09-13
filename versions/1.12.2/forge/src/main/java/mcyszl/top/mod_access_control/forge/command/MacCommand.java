// SPDX-License-Identifier: Apache-2.0
// Copyright 2024-2026 mcyszl.top (Luoyangan)

package mcyszl.top.mod_access_control.forge.command;

import mcyszl.top.mod_access_control.core.Mac;
import mcyszl.top.mod_access_control.core.feedback.HelpText;
import mcyszl.top.mod_access_control.core.i18n.CommandText;
import mcyszl.top.mod_access_control.core.i18n.I18n;
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
 * <p>子命令与新版 Brigadier 实现保持一致：help [页码] / status / recent /
 * check &lt;玩家&gt; / audit &lt;玩家&gt; / learn &lt;玩家&gt; &lt;whitelist|blacklist&gt; /
 * reload / save / recheck / enabled &lt;true|false&gt; / dryrun &lt;true|false&gt; /
 * mode &lt;whitelist|blacklist|switch&gt; / active &lt;whitelist|blacklist&gt; /
 * exempt list|add|remove / allowedmac list|add|remove /
 * required list|add|remove / whitelist list|add|remove /
 * blacklist list|add|remove。</p>
 *
 * <p>v1.1：黑白名单条目支持 {@code *} 通配符与可选版本约束
 * （如 {@code /mac whitelist add sodium >=1.0}）；未设置约束 = 匹配全部版本。
 * 回显文案统一走核心 {@link CommandText}（随服务端语言切换）。</p>
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
        // 回显文案按命令执行者的客户端语言渲染（控制台/命令方块保持服务端语言）。
        I18n.withLocale(sourceLanguage(sender), () -> {
            try {
                dispatch(sender, args);
            } catch (MacCmdException ex) {
                send(sender, ex.getMessage(), TextFormatting.RED);
            } catch (Exception ex) {
                send(sender, CommandText.runFailure(String.valueOf(ex.getMessage())), TextFormatting.RED);
            }
            return null;
        });
    }

    /** 命令执行者若为玩家则返回其客户端语言；否则返回 null（用服务端语言）。 */
    private static String sourceLanguage(ICommandSender sender) {
        if (sender instanceof net.minecraft.entity.player.EntityPlayerMP) {
            return Holder.bridge().clientLanguage(
                    ((net.minecraft.entity.player.EntityPlayerMP) sender).getUniqueID().toString());
        }
        return null;
    }

    private void dispatch(ICommandSender sender, String[] args) {
        String sub = args.length == 0 ? "status" : args[0].toLowerCase();
        if (sub.equals("help")) {
            int page = 1;
            if (args.length >= 2) {
                try {
                    page = Integer.parseInt(args[1]);
                } catch (NumberFormatException ignore) {
                    page = 1;
                }
            }
            for (String line : HelpText.page(page)) {
                send(sender, line, TextFormatting.GREEN);
            }
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
                send(sender, CommandText.recentEmpty(), TextFormatting.GREEN);
                return;
            }
            send(sender, CommandText.recentHeader(v.size()), TextFormatting.GREEN);
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
            String name = argOrThrow(args, 1, CommandText.usageCheck());
            String s = service().sessionStatus(name);
            if (s == null) {
                throw new MacCmdException(CommandText.checkNotFound(name));
            }
            send(sender, s, TextFormatting.GREEN);
            return;
        }
        if (sub.equals("audit")) {
            String name = argOrThrow(args, 1, CommandText.usageAudit());
            List<String> lines = service().auditLines(name);
            if (lines.isEmpty()) {
                send(sender, CommandText.auditEmpty(name), TextFormatting.GREEN);
                return;
            }
            send(sender, CommandText.auditHeader(name), TextFormatting.GREEN);
            for (String l : lines) {
                send(sender, "  - " + l, TextFormatting.GREEN);
            }
            return;
        }
        if (sub.equals("learn")) {
            String name = argOrThrow(args, 1, CommandText.usageLearn());
            String list = argOrThrow(args, 2, CommandText.usageLearn());
            PolicyMode pm = PolicyMode.byKey(list);
            if (pm == PolicyMode.SWITCH) {
                throw new MacCmdException(CommandText.errLearnMode());
            }
            send(sender, service().learn(name, pm == PolicyMode.WHITELIST), TextFormatting.GREEN);
            return;
        }
        if (sub.equals("reload")) {
            service().configManager().load();
            send(sender, CommandText.reload(), TextFormatting.GREEN);
            return;
        }
        if (sub.equals("save")) {
            service().configManager().save();
            send(sender, CommandText.save(), TextFormatting.GREEN);
            return;
        }
        if (sub.equals("recheck")) {
            service().forceRecheckAll();
            send(sender, CommandText.recheck(), TextFormatting.GREEN);
            return;
        }
        if (sub.equals("enabled")) {
            boolean value = boolArg(args);
            update(cfg -> cfg.setEnabled(value));
            send(sender, CommandText.enabled(value), TextFormatting.GREEN);
            return;
        }
        if (sub.equals("dryrun")) {
            boolean value = boolArg(args);
            update(cfg -> cfg.setDryRun(value));
            send(sender, CommandText.dryrun(value), TextFormatting.GREEN);
            return;
        }
        if (sub.equals("mode")) {
            String value = argOrThrow(args, 1, CommandText.usageMode());
            PolicyMode pm = PolicyMode.byKey(value);
            update(cfg -> cfg.getPolicy().setMode(pm.key()));
            send(sender, CommandText.mode(pm.key(), pm == PolicyMode.SWITCH), TextFormatting.GREEN);
            return;
        }
        if (sub.equals("active")) {
            String value = argOrThrow(args, 1, CommandText.usageActive());
            PolicyMode pm = PolicyMode.byKey(value);
            if (pm == PolicyMode.SWITCH) {
                throw new MacCmdException(CommandText.errActiveMode());
            }
            update(cfg -> cfg.getPolicy().setActiveMode(pm.key()));
            send(sender, CommandText.active(pm.key()), TextFormatting.GREEN);
            return;
        }
        if (sub.equals("exempt")) {
            listOp(sender, args, "exempt", new ListOp() {
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
            listOp(sender, args, "allowedmac", new ListOp() {
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
                    send(sender, CommandText.requiredEmpty(c.requiredMode().key()), TextFormatting.GREEN);
                    return;
                }
                send(sender, CommandText.requiredHeader(c.requiredMode().key()), TextFormatting.GREEN);
                for (RequiredModRule r : c.getRequiredMods()) {
                    send(sender, "  - " + r, TextFormatting.GREEN);
                }
                return;
            }
            if (args.length >= 3 && "add".equalsIgnoreCase(args[1])) {
                String id = args[2];
                if (id.equalsIgnoreCase(Mac.MOD_ID)) {
                    throw new MacCmdException(CommandText.errSelfRequired());
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
                send(sender, CommandText.requiredAdded(rule.toString()), TextFormatting.GREEN);
                return;
            }
            if (args.length >= 3 && "remove".equalsIgnoreCase(args[1])) {
                String id = args[2];
                boolean had = false;
                for (RequiredModRule r : cfg().getRequiredMods()) {
                    if (r.getId().equalsIgnoreCase(id)) {
                        had = true;
                        break;
                    }
                }
                if (!had) {
                    throw new MacCmdException(CommandText.requiredNotFound(id));
                }
                update(cfg -> cfg.getRequiredMods().removeIf(r -> r.getId().equalsIgnoreCase(id)));
                send(sender, CommandText.requiredRemoved(id), TextFormatting.GREEN);
                return;
            }
            throw new MacCmdException(CommandText.usageRequired());
        }
        if (sub.equals("whitelist") || sub.equals("blacklist")) {
            policyOp(sender, args, sub.equals("whitelist"));
            return;
        }
        throw new MacCmdException(CommandText.errUnknownSub(sub, getUsage(sender)));
    }

    // ------------------------------------------------------------------ 字符串列表型子命令公共壳（exempt / allowedmac）

    private interface ListOp {
        List<String> list();

        void add(String id);

        void remove(String id);
    }

    /** 文案键 → CommandText 方法（exempt / allowedmac 两套）。 */
    private void listOp(ICommandSender sender, String[] args, String kind, ListOp op) {
        boolean exempt = kind.equals("exempt");
        if (args.length >= 2 && "list".equalsIgnoreCase(args[1])) {
            List<String> ids = op.list();
            if (ids.isEmpty()) {
                send(sender, exempt ? CommandText.exemptEmpty() : CommandText.allowedMacEmpty(),
                        TextFormatting.GREEN);
                return;
            }
            send(sender, exempt
                            ? CommandText.exemptHeader(ids.size(), cfg().isExemptOps())
                            : CommandText.allowedMacHeader(ids.size()),
                    TextFormatting.GREEN);
            for (String s : ids) {
                send(sender, "  - " + s, TextFormatting.GREEN);
            }
            return;
        }
        if (args.length >= 3 && "add".equalsIgnoreCase(args[1])) {
            String id = args[2];
            for (String i : op.list()) {
                if (i.equalsIgnoreCase(id)) {
                    send(sender, exempt ? CommandText.exemptExists(id) : CommandText.allowedMacExists(id),
                            TextFormatting.GREEN);
                    return;
                }
            }
            if (!exempt && "*".equals(id)) {
                op.add(id);
                send(sender, CommandText.allowedMacCleared(), TextFormatting.GREEN);
                return;
            }
            op.add(id);
            send(sender, exempt ? CommandText.exemptAdded(id) : CommandText.allowedMacAdded(id),
                    TextFormatting.GREEN);
            return;
        }
        if (args.length >= 3 && "remove".equalsIgnoreCase(args[1])) {
            String id = args[2];
            boolean had = false;
            for (String i : op.list()) {
                if (i.equalsIgnoreCase(id)) {
                    had = true;
                    break;
                }
            }
            if (!had) {
                throw new MacCmdException(exempt
                        ? CommandText.exemptNotFound(id) : CommandText.allowedMacNotFound(id));
            }
            op.remove(id);
            send(sender, exempt ? CommandText.exemptRemoved(id) : CommandText.allowedMacRemoved(id),
                    TextFormatting.GREEN);
            return;
        }
        throw new MacCmdException(CommandText.usageList(args[0]));
    }

    // ------------------------------------------------------------------ 白名单 / 黑名单（PolicyEntry：通配符 + 可选版本约束）

    private void policyOp(ICommandSender sender, String[] args, boolean whitelist) {
        String name = CommandText.listName(whitelist);
        if (args.length >= 2 && "list".equalsIgnoreCase(args[1])) {
            List<PolicyEntry> list = policy(whitelist);
            if (list.isEmpty()) {
                send(sender, CommandText.listEmpty(name), TextFormatting.GREEN);
                return;
            }
            send(sender, CommandText.listHeader(name, list.size()), TextFormatting.GREEN);
            for (PolicyEntry e : list) {
                send(sender, "  - " + e, TextFormatting.GREEN);
            }
            return;
        }
        if (args.length >= 3 && "add".equalsIgnoreCase(args[1])) {
            String id = args[2];
            for (PolicyEntry e : policy(whitelist)) {
                if (e.getId() != null && e.getId().equalsIgnoreCase(id)) {
                    send(sender, CommandText.listExists(id, name), TextFormatting.GREEN);
                    return;
                }
            }
            StringBuilder bounds = new StringBuilder();
            for (int i = 3; i < args.length; i++) {
                if (bounds.length() > 0) {
                    bounds.append(' ');
                }
                bounds.append(args[i]);
            }
            PolicyEntry entry = new PolicyEntry(id);
            if (bounds.length() > 0) {
                entry.applySpec(bounds.toString());
            }
            entry.normalize();
            update(c -> policy(c, whitelist).add(entry));
            send(sender, CommandText.listAdded(name, entry.toString()), TextFormatting.GREEN);
            return;
        }
        if (args.length >= 3 && "remove".equalsIgnoreCase(args[1])) {
            String id = args[2];
            boolean had = false;
            for (PolicyEntry e : policy(whitelist)) {
                if (e.getId() != null && e.getId().equalsIgnoreCase(id)) {
                    had = true;
                    break;
                }
            }
            if (!had) {
                throw new MacCmdException(CommandText.listNotFound(name, id));
            }
            update(c -> policy(c, whitelist).removeIf(e -> e.getId() != null
                    && e.getId().equalsIgnoreCase(id)));
            send(sender, CommandText.listRemoved(name, id), TextFormatting.GREEN);
            return;
        }
        throw new MacCmdException(CommandText.usagePolicy(args[0]));
    }

    private static List<PolicyEntry> policy(MacConfig cfg, boolean whitelist) {
        return whitelist ? cfg.getPolicy().getWhitelist() : cfg.getPolicy().getBlacklist();
    }

    private static List<PolicyEntry> policy(boolean whitelist) {
        return policy(cfg(), whitelist);
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
        String v = argOrThrow(args, 1, CommandText.usageBool());
        if ("true".equalsIgnoreCase(v)) {
            return true;
        }
        if ("false".equalsIgnoreCase(v)) {
            return false;
        }
        throw new MacCmdException(CommandText.errBool(v));
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
                List<String> pages = new ArrayList<>();
                for (int i = 1; i <= HelpText.totalPages(); i++) {
                    pages.add(String.valueOf(i));
                }
                return getListOfStringsMatchingLastWord(args, pages);
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
                return getListOfStringsMatchingLastWord(args, ACTIVE_MODES);
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
                List<String> ids = new ArrayList<>();
                for (PolicyEntry e : policy(sub.equals("whitelist"))) {
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
