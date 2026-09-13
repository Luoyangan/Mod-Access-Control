// SPDX-License-Identifier: Apache-2.0
// Copyright 2024-2026 mcyszl.top (Luoyangan)

package mcyszl.top.mod_access_control.core.config;

import com.google.gson.TypeAdapter;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import com.google.gson.stream.JsonWriter;
import mcyszl.top.mod_access_control.core.model.PolicyEntry;
import mcyszl.top.mod_access_control.core.model.RequiredModRule;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * {@link PolicyEntry} 的 Gson 适配器：兼容 v1.0 的“纯字符串条目”与
 * v1.1 的“对象条目（id + 可选版本约束 bounds）”两种 JSON 形态。
 *
 * <p>写出规则：无版本约束写回纯字符串（文件保持简洁、与旧版一致）；
 * 有约束才写对象 {@code {"id":"x","bounds":[{"op":">=","version":"1.0"}]}}。</p>
 *
 * <p>注意 1.12.2 自带 Gson 2.8.0：仅使用 stream API，不依赖 2.8.6+ 方法。</p>
 */
public final class PolicyEntryTypeAdapter extends TypeAdapter<PolicyEntry> {

    @Override
    public void write(JsonWriter out, PolicyEntry value) throws IOException {
        if (value == null || value.getId() == null) {
            out.nullValue();
            return;
        }
        if (!value.hasBounds()) {
            out.value(value.getId());
            return;
        }
        out.beginObject();
        out.name("id").value(value.getId());
        out.name("bounds");
        out.beginArray();
        for (RequiredModRule.Bound b : value.getBounds()) {
            out.beginObject();
            out.name("op").value(b.getOp());
            out.name("version").value(b.getVersion());
            out.endObject();
        }
        out.endArray();
        out.endObject();
    }

    @Override
    public PolicyEntry read(JsonReader in) throws IOException {
        if (in.peek() == JsonToken.NULL) {
            in.nextNull();
            return null;
        }
        if (in.peek() == JsonToken.STRING) {
            return new PolicyEntry(in.nextString());
        }
        if (in.peek() != JsonToken.BEGIN_OBJECT) {
            // 非字符串/对象的其他形态：跳过，视作无效条目。
            in.skipValue();
            return null;
        }
        PolicyEntry entry = new PolicyEntry();
        in.beginObject();
        while (in.hasNext()) {
            String name = in.nextName();
            if ("id".equals(name) && in.peek() == JsonToken.STRING) {
                entry.setId(in.nextString());
            } else if ("bounds".equals(name) && in.peek() == JsonToken.BEGIN_ARRAY) {
                in.beginArray();
                List<RequiredModRule.Bound> bounds = new ArrayList<>();
                while (in.hasNext()) {
                    RequiredModRule.Bound b = readBound(in);
                    if (b != null) {
                        bounds.add(b);
                    }
                }
                in.endArray();
                entry.setBounds(bounds);
            } else {
                in.skipValue();
            }
        }
        in.endObject();
        return entry;
    }

    private RequiredModRule.Bound readBound(JsonReader in) throws IOException {
        if (in.peek() != JsonToken.BEGIN_OBJECT) {
            in.skipValue();
            return null;
        }
        String op = "=";
        String version = null;
        in.beginObject();
        while (in.hasNext()) {
            String name = in.nextName();
            if ("op".equals(name) && in.peek() == JsonToken.STRING) {
                op = in.nextString();
            } else if ("version".equals(name) && in.peek() == JsonToken.STRING) {
                version = in.nextString();
            } else {
                in.skipValue();
            }
        }
        in.endObject();
        if (version == null || version.trim().isEmpty()) {
            return null;
        }
        return new RequiredModRule.Bound(op, version);
    }
}
