// SPDX-License-Identifier: Apache-2.0
// Copyright 2024-2026 mcyszl.top (Luoyangan)

package mcyszl.top.mod_access_control.forge.command;

import mcyszl.top.mod_access_control.core.Mac;
import mcyszl.top.mod_access_control.core.i18n.Lang;
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
 * lang &lt;zh_cn|zh_tw|en_us|ja_jp|ru_ru&gt; / exempt list|add|remove / allowedmac list|add|remove /
 * required list|add|remove / whitelist list|add|remove &lt;id&gt; [约束] /
 * blacklist list|add|remove &lt;id&gt; [约束]。</p>
 */
public final class MacCommand extends CommandBase {

    private static final List<String> ROOT_SUBS = Arrays.asList(
            "help", "status", "recent", "check", "audit", "learn", "reload", "save", "recheck",
            "enabled", "dryrun", "mode", "active", "exempt", "allowedmac", "lang",
            "required", "whitelist", "blacklist");
    private static final List<String> LIST_SUBS = Arrays.asList("list", "add", "remove");
    private static final List<String> BOOLS = Arrays.asList("true", "false");
    private static final List<String> MODES = Arrays.asList("whitelist", "blacklist", "switch");
    private static final List<String> ACTIVE_MODES = Arrays.asList("whitelist", "blacklist");
    private static final List<String> LEARN_MODES = Arrays.asList("whitelist", "blacklist", "required");
    private static final List<String> LANGS = Arrays.asList(
            Lang.ZH_CN, Lang.ZH_TW, Lang.EN_US, Lang.JA_JP, Lang.RU_RU);

    @Override
    public String getName() {
        return "mac";
    }

    @Override
    public String getUsage(ICommandSender sender) {
        return "/mac <help|status|recent|check|audit|learn|reload|save|recheck|enabled|dryrun|mode|active|lang|exempt|allowedmac|required|whitelist|blacklist> ...";
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
            send(sender, Lang.tr("[MAC] Command failed: ") + ex.getMessage(), TextFormatting.RED);
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
                send(sender, Lang.tr("No violations recorded."), TextFormatting.GREEN);
                return;
            }
            send(sender, Lang.tr("Recent violations (newest first, ") + v.size() + Lang.tr(" total):"),
                    TextFormatting.GREEN);
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
            String name = argOrThrow(args, 1, Lang.tr("Usage: /mac check <player>"));
            String s = service().sessionStatus(name);
            if (s == null) {
                throw new MacCmdException(Lang.tr("[MAC] Player not found: ") + name);
            }
            send(sender, s, TextFormatting.GREEN);
            return;
        }
        if (sub.equals("audit")) {
            String name = argOrThrow(args, 1, Lang.tr("Usage: /mac audit <player>"));
            List<String> lines = service().auditLines(name);
            if (lines.isEmpty()) {
                send(sender, Lang.tr("No mod history for {}.", name), TextFormatting.GREEN);
                return;
            }
            send(sender, name + Lang.tr("'s mod history (newest first):"), TextFormatting.GREEN);
            for (String l : lines) {
                send(sender, "  - " + l, TextFormatting.GREEN);
            }
            return;
        }
        if (sub.equals("learn")) {
            String name = argOrThrow(args, 1, Lang.tr("Usage: /mac learn <player> <whitelist|blacklist|required>"));
            String list = argOrThrow(args, 2, Lang.tr("Usage: /mac learn <player> <whitelist|blacklist|required>"));
            if (list.equalsIgnoreCase("required")) {
                send(sender, service().learnRequired(name), TextFormatting.GREEN);
                return;
            }
            PolicyMode pm = PolicyMode.byKey(list);
            if (pm == PolicyMode.SWITCH) {
                throw new MacCmdException(Lang.tr("learn only accepts whitelist or blacklist."));
            }
            send(sender, service().learn(name, pm == PolicyMode.WHITELIST), TextFormatting.GREEN);
            return;
        }
        if (sub.equals("reload")) {
            service().configManager().load();
            send(sender, Lang.tr("Reloaded from config file. Run /mac recheck to re-check online players with the latest rules."),
                    TextFormatting.GREEN);
            return;
        }
        if (sub.equals("save")) {
            service().configManager().save();
            send(sender, Lang.tr("Current config saved to file."), TextFormatting.GREEN);
            return;
        }
        if (sub.equals("recheck")) {
            service().forceRecheckAll();
            send(sender, Lang.tr("Full re-check of online players completed with current rules."), TextFormatting.GREEN);
            return;
        }
        if (sub.equals("enabled")) {
            boolean value = boolArg(args);
            update(cfg -> cfg.setEnabled(value));
            send(sender, Lang.tr("Master switch set to: ") + value
                    + Lang.tr(" (when off, clients are no longer forced through admission checks)"), TextFormatting.GREEN);
            return;
        }
        if (sub.equals("dryrun")) {
            boolean value = boolArg(args);
            update(cfg -> cfg.setDryRun(value));
            send(sender, Lang.tr("Dry-run mode set to: ") + value
                    + (value ? Lang.tr(" (violations are only logged, no one is kicked)") : ""), TextFormatting.GREEN);
            return;
        }
        if (sub.equals("mode")) {
            String value = argOrThrow(args, 1, Lang.tr("Usage: /mac mode <whitelist|blacklist|switch>"));
            PolicyMode pm = PolicyMode.byKey(value);
            update(cfg -> cfg.getPolicy().setMode(pm.key()));
            String note = pm == PolicyMode.SWITCH
                    ? Lang.tr(" Tip: set the active policy with /mac active <whitelist|blacklist>.")
                    : "";
            send(sender, Lang.tr("Policy mode set to: ") + pm.key() + note, TextFormatting.GREEN);
            return;
        }
        if (sub.equals("active")) {
            String value = argOrThrow(args, 1, Lang.tr("Usage: /mac active <whitelist|blacklist>"));
            PolicyMode pm = PolicyMode.byKey(value);
            if (pm == PolicyMode.SWITCH) {
                throw new MacCmdException(Lang.tr("active only accepts whitelist or blacklist."));
            }
            update(cfg -> cfg.getPolicy().setActiveMode(pm.key()));
            send(sender, Lang.tr("Active policy set to: ") + pm.key(), TextFormatting.GREEN);
            return;
        }
        if (sub.equals("lang")) {
            if (args.length < 2 || args[1] == null || args[1].isEmpty()) {
                throw new MacCmdException(Lang.tr("Usage: /mac lang <zh_cn|zh_tw|en_us|ja_jp|ru_ru>"));
            }
            String key = Lang.normalize(args[1]);
            update(cfg -> cfg.setLanguage(key));
            send(sender, Lang.tr("Server language set to: ") + key, TextFormatting.GREEN);
            return;
        }
        if (sub.equals("exempt")) {
            listOp(sender, args, Lang.tr("exemptions"), new ListOp() {
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
            listOp(sender, args, Lang.tr("allowed versions"), new ListOp() {
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
                    send(sender, Lang.tr("Required mod list is empty (check mode: ") + c.requiredMode().key()
                            + Lang.tr(")."), TextFormatting.GREEN);
                    return;
                }
                send(sender, Lang.tr("Required mods (check mode: ") + c.requiredMode().key() + Lang.tr("):"),
                        TextFormatting.GREEN);
                for (RequiredModRule r : c.getRequiredMods()) {
                    send(sender, "  - " + r.getId() + Lang.tr("  constraint: ") + r.constraintText(), TextFormatting.GREEN);
                }
                return;
            }
            if (args.length >= 3 && "add".equalsIgnoreCase(args[1])) {
                String id = args[2];
                if (id.equalsIgnoreCase(Mac.MOD_ID)) {
                    throw new MacCmdException(Lang.tr("Cannot add this mod itself to the required list."));
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
                send(sender, Lang.tr("Added required mod: ") + rule.toString(), TextFormatting.GREEN);
                return;
            }
            if (args.length >= 3 && "remove".equalsIgnoreCase(args[1])) {
                String id = args[2];
                boolean had = cfg().getRequiredMods().stream().anyMatch(r -> r.getId().equalsIgnoreCase(id));
                if (!had) {
                    throw new MacCmdException(Lang.tr("Not in required list: ") + id);
                }
                update(cfg -> cfg.getRequiredMods().removeIf(r -> r.getId().equalsIgnoreCase(id)));
                send(sender, Lang.tr("Removed required mod: ") + id, TextFormatting.GREEN);
                return;
            }
            throw new MacCmdException(Lang.tr("Usage: /mac {} list | add <id> [constraint] | remove <id>", args[0]));
        }
        if (sub.equals("whitelist")) {
            policyOp(sender, args, true);
            return;
        }
        if (sub.equals("blacklist")) {
            policyOp(sender, args, false);
            return;
        }
        throw new MacCmdException(Lang.tr("Unknown subcommand: ") + sub + ". " + getUsage(sender));
    }

    // ------------------------------------------------------------------ 分页帮助

    private List<String> helpLines() {
        List<String> lines = new ArrayList<String>();
        lines.add(Lang.tr("/mac or /mac status - show current policy and session status"));
        lines.add(Lang.tr("/mac recent - recent violations (up to 20 entries)"));
        lines.add(Lang.tr("/mac check <player> - view a player's session status"));
        lines.add(Lang.tr("/mac audit <player> - view a player's mod history"));
        lines.add(Lang.tr("/mac learn <player> <whitelist|blacklist|required> - build lists from a player's mods"));
        lines.add(Lang.tr("/mac reload - reload rules from the config file"));
        lines.add(Lang.tr("/mac save - save current config to file"));
        lines.add(Lang.tr("/mac recheck - re-check online players with latest rules"));
        lines.add(Lang.tr("/mac enabled <true|false> - master switch"));
        lines.add(Lang.tr("/mac dryrun <true|false> - dry-run switch (no kicks)"));
        lines.add(Lang.tr("/mac mode <whitelist|blacklist|switch> - set policy mode"));
        lines.add(Lang.tr("/mac active <whitelist|blacklist> - active policy in switch mode"));
        lines.add(Lang.tr("/mac exempt list|add|remove <player> - manage exemptions"));
        lines.add(Lang.tr("/mac allowedmac list|add|remove <version> - manage allowed MAC versions"));
        lines.add(Lang.tr("/mac required list|add|remove - manage required mods (add supports version constraints)"));
        lines.add(Lang.tr("/mac whitelist list|add|remove <id> [constraint] - manage whitelist (add supports * wildcard and version constraints)"));
        lines.add(Lang.tr("/mac blacklist list|add|remove <id> [constraint] - manage blacklist (add supports * wildcard and version constraints)"));
        lines.add(Lang.tr("/mac lang <zh_cn|en_us> - set server message language"));
        return lines;
    }

    private void sendHelp(ICommandSender sender, String[] args) {
        List<String> lines = helpLines();
        int perPage = 10;
        int pages = (lines.size() + perPage - 1) / perPage;
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
        send(sender, Lang.tr("===== /mac help page ") + page + "/" + pages + Lang.tr(" ====="), TextFormatting.GREEN);
        for (int i = (page - 1) * perPage; i < page * perPage && i < lines.size(); i++) {
            send(sender, lines.get(i), TextFormatting.GREEN);
        }
        if (page < pages) {
            send(sender, Lang.tr("Next page: /mac help ") + (page + 1), TextFormatting.GREEN);
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
                send(sender, name + Lang.tr(" is empty."), TextFormatting.GREEN);
                return;
            }
            send(sender, name + Lang.tr(" (") + ids.size() + Lang.tr(" entries") + Lang.tr("):"),
                    TextFormatting.GREEN);
            for (String s : ids) {
                send(sender, "  - " + s, TextFormatting.GREEN);
            }
            return;
        }
        if (args.length >= 3 && "add".equalsIgnoreCase(args[1])) {
            String id = args[2];
            if (op.list().stream().anyMatch(i -> i.equalsIgnoreCase(id))) {
                send(sender, Lang.tr("Already in {}: {}", name, id), TextFormatting.GREEN);
                return;
            }
            op.add(id);
            send(sender, Lang.tr("Added to {}: ", name) + id, TextFormatting.GREEN);
            return;
        }
        if (args.length >= 3 && "remove".equalsIgnoreCase(args[1])) {
            String id = args[2];
            boolean had = op.list().stream().anyMatch(i -> i.equalsIgnoreCase(id));
            if (!had) {
                throw new MacCmdException(Lang.tr("Not found in {}: ", name) + id);
            }
            op.remove(id);
            send(sender, Lang.tr("Removed from {}: ", name) + id, TextFormatting.GREEN);
            return;
        }
        throw new MacCmdException(Lang.tr("Usage: /mac {} list | add <id> | remove <id>", args[0]));
    }

    // ------------------------------------------------------------------ 白/黑名单（PolicyEntry，支持 * 通配符与版本约束）

    private void policyOp(ICommandSender sender, String[] args, boolean whitelist) {
        String name = whitelist ? Lang.tr("whitelist") : Lang.tr("blacklist");
        if (args.length >= 2 && "list".equalsIgnoreCase(args[1])) {
            List<String> lines = service().policyLines(whitelist);
            if (lines.isEmpty()) {
                send(sender, name + Lang.tr(" is empty."), TextFormatting.GREEN);
                return;
            }
            send(sender, name + Lang.tr(" (") + lines.size()
                    + Lang.tr(" entries; add supports * wildcard and version constraints):"), TextFormatting.GREEN);
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
        throw new MacCmdException(Lang.tr("Usage: /mac {} list | add <id> [constraint] | remove <id>", args[0]));
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
        String v = argOrThrow(args, 1, Lang.tr("Usage: a <true|false> argument is required"));
        if ("true".equalsIgnoreCase(v)) {
            return true;
        }
        if ("false".equalsIgnoreCase(v)) {
            return false;
        }
        throw new MacCmdException(Lang.tr("Argument must be true or false: ") + v);
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
            if (sub.equals("lang")) {
                return getListOfStringsMatchingLastWord(args, LANGS);
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
                List<String> ids = new ArrayList<String>();
                for (PolicyEntry e : src) {
                    ids.add(e.getId());
                }
                return getListOfStringsMatchingLastWord(args, ids);
            }
        }
        return Collections.emptyList();
    }

    private static List<String> requiredIds() {
        List<String> ids = new ArrayList<String>();
        for (RequiredModRule r : cfg().getRequiredMods()) {
            ids.add(r.getId());
        }
        return ids;
    }

    private static List<String> playerNames(MinecraftServer server, String[] args) {
        List<String> names = new ArrayList<String>();
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
