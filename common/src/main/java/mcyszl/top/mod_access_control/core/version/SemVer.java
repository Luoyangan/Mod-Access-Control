package mcyszl.top.mod_access_control.core.version;

import java.util.Objects;

/**
 * 轻量语义化版本比较器，用于“版本范围检查 / 严格匹配”。
 *
 * <p>支持格式：{@code major[.minor[.patch[.build]]]}，允许带常见后缀
 * （beta/rc 等通过忽略处理）。比较按点分数字段逐级进行，数字越界视为 0。</p>
 */
public final class SemVer implements Comparable<SemVer> {

    private final String raw;
    private final int[] parts;

    private SemVer(String raw, int[] parts) {
        this.raw = raw;
        this.parts = parts;
    }

    public String raw() {
        return raw;
    }

    /**
     * 解析版本字符串。
     *
     * @return 版本对象；若无法识别数字主版本号返回 {@code null}（视作“无版本信息”）。
     */
    public static SemVer parse(String text) {
        if (text == null) {
            return null;
        }
        String t = text.trim();
        if (t.isEmpty() || "*".equals(t) || "-".equals(t) || "unknown".equalsIgnoreCase(t)) {
            return null;
        }
        // 掐掉常见修饰：1.0.0-beta.1 / 1.0.0+build.5
        String core = t.split("[+\\-]")[0];
        String[] seg = core.split("\\.");
        int[] nums = new int[4];
        boolean any = false;
        for (int i = 0; i < seg.length && i < nums.length; i++) {
            String s = seg[i].trim();
            if (s.isEmpty()) {
                nums[i] = 0;
                continue;
            }
            try {
                nums[i] = Integer.parseInt(s);
                any = true;
            } catch (NumberFormatException e) {
                // 非数字段（如 "v" 前缀被剥离后仍异常）视为整体不可解析
                return null;
            }
        }
        if (!any) {
            return null;
        }
        return new SemVer(t, nums);
    }

    @Override
    public int compareTo(SemVer o) {
        for (int i = 0; i < 4; i++) {
            int c = Integer.compare(part(i), o.part(i));
            if (c != 0) {
                return c;
            }
        }
        return 0;
    }

    private int part(int i) {
        return i < parts.length ? parts[i] : 0;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof SemVer semVer)) {
            return false;
        }
        return compareTo(semVer) == 0;
    }

    @Override
    public int hashCode() {
        return Objects.hash(parts[0], part(1), part(2), part(3));
    }

    @Override
    public String toString() {
        return raw;
    }
}
