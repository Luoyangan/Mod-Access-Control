package mcyszl.top.mod_access_control.core.storage;

import com.google.gson.JsonSyntaxException;
import mcyszl.top.mod_access_control.core.Mac;
import mcyszl.top.mod_access_control.core.network.Json;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;

/**
 * 玩家客户端 Mod 清单的持久记录（追加式 JSONL 文件）。
 *
 * <p>纯 Java 无第三方依赖；每行一条 {@link ModRecord}。文件过大时自动裁剪，
 * 仅保留最近 {@link #MAX_RECORDS} 条，保证长期运行不膨胀。</p>
 */
public final class ModHistoryStore {

    /** 保留的最大记录条数（超出时裁剪旧记录）。 */
    public static final int MAX_RECORDS = 20000;

    private final Path file;
    /** 本进程内累计追加次数（触发周期性裁剪判断）。 */
    private long writes;

    public ModHistoryStore(Path file) {
        this.file = file;
    }

    /** 追加一条记录；失败仅记日志，不影响主流程。 */
    public synchronized void append(ModRecord rec) {
        if (rec == null) {
            return;
        }
        try {
            if (file.getParent() != null) {
                Files.createDirectories(file.getParent());
            }
            Files.write(file, (Json.toJson(rec) + System.lineSeparator())
                            .getBytes(StandardCharsets.UTF_8),
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
            if (++writes % 64 == 0) {
                trimIfNeeded();
            }
        } catch (IOException e) {
            Mac.logger().warn("[MAC] 客户端Mod记录写入失败: {}", e.getMessage());
        }
    }

    /** 读取与给定玩家（名称或 uuid）匹配的记录，最新在前。 */
    public List<ModRecord> forPlayer(String nameOrUuid) {
        List<ModRecord> out = new ArrayList<>();
        if (nameOrUuid == null) {
            return out;
        }
        for (ModRecord r : readAll()) {
            if (r.matches(nameOrUuid)) {
                out.add(0, r);
            }
        }
        return out;
    }

    /** 该玩家最近一条记录；无则 null。 */
    public ModRecord latestFor(String nameOrUuid) {
        for (ModRecord r : forPlayer(nameOrUuid)) {
            return r; // forPlayer 已按最新在前
        }
        return null;
    }

    /** 若文件超过阈值则裁剪，只保留最近 MAX_RECORDS 行。 */
    private void trimIfNeeded() throws IOException {
        if (!Files.exists(file)) {
            return;
        }
        long size;
        try {
            size = Files.size(file);
        } catch (IOException e) {
            return;
        }
        if (size < 8 * 1024 * 1024) {
            return;
        }
        List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
        if (lines.size() <= MAX_RECORDS) {
            return;
        }
        int from = lines.size() - MAX_RECORDS;
        List<String> keep = new ArrayList<>(lines.subList(from, lines.size()));
        Files.write(file, keep, StandardCharsets.UTF_8, StandardOpenOption.TRUNCATE_EXISTING);
    }

    private List<ModRecord> readAll() {
        List<ModRecord> out = new ArrayList<>();
        if (!Files.exists(file)) {
            return out;
        }
        try {
            for (String line : Files.readAllLines(file, StandardCharsets.UTF_8)) {
                if (line.trim().isEmpty()) {
                    continue;
                }
                try {
                    ModRecord r = Json.gson().fromJson(line, ModRecord.class);
                    if (r != null) {
                        out.add(r);
                    }
                } catch (JsonSyntaxException ignore) {
                    // 跳过个别损坏行
                }
            }
        } catch (IOException e) {
            Mac.logger().warn("[MAC] 客户端Mod记录读取失败: {}", e.getMessage());
        }
        return out;
    }
}
