package com.phantasm.briefing.network.packet;

import com.phantasm.briefing.data.ManualNodeSpec;
import com.phantasm.briefing.data.ManualPageSpec;
import com.phantasm.briefing.data.ManualReferenceSpec;
import com.phantasm.briefing.data.ManualSpec;
import com.phantasm.briefing.data.ManualUnlockType;
import net.minecraft.network.FriendlyByteBuf;
import java.util.List;

/** The journal's server-filtered handbook snapshot and precise lesson links. */
public final class ManualPacketCodec {
    private ManualPacketCodec() {}

    public static void writeManuals(FriendlyByteBuf buffer, List<ManualSpec> manuals) {
        buffer.writeCollection(manuals, (buf, manual) -> {
            buf.writeUtf(manual.manualId());
            buf.writeUtf(manual.title());
            buf.writeUtf(manual.description());
            buf.writeUtf(manual.category());
            buf.writeUtf(manual.cover());
            buf.writeVarInt(manual.sortOrder());
            buf.writeCollection(manual.sections(), ManualPacketCodec::writeNode);
        });
    }

    public static List<ManualSpec> readManuals(FriendlyByteBuf buffer) {
        return buffer.readList(buf -> new ManualSpec(buf.readUtf(), buf.readUtf(), buf.readUtf(),
                buf.readUtf(), buf.readUtf(), buf.readVarInt(), buf.readList(ManualPacketCodec::readNode)));
    }

    private static void writeNode(FriendlyByteBuf buffer, ManualNodeSpec node) {
        buffer.writeBoolean(node.isGroup());
        buffer.writeUtf(node.nodeId());
        buffer.writeUtf(node.title());
        if (node.isGroup()) buffer.writeCollection(node.children(), ManualPacketCodec::writeNode);
        else buffer.writeCollection(node.pages(), (buf, page) -> {
            buf.writeUtf(page.pageId());
            buf.writeUtf(page.title());
            buf.writeUtf(page.text());
            buf.writeUtf(page.image());
        });
    }

    private static ManualNodeSpec readNode(FriendlyByteBuf buffer) {
        boolean group = buffer.readBoolean();
        String id = buffer.readUtf(), title = buffer.readUtf();
        if (group) return new ManualNodeSpec("group", id, title,
                buffer.readList(ManualPacketCodec::readNode), List.of(), ManualUnlockType.ALWAYS, "", "", "");
        return new ManualNodeSpec("lesson", id, title, List.of(),
                buffer.readList(buf -> new ManualPageSpec(buf.readUtf(), buf.readUtf(), buf.readUtf(), buf.readUtf())), ManualUnlockType.ALWAYS, "", "", "");
    }

    public static void writeRefs(FriendlyByteBuf buffer, List<ManualReferenceSpec> refs) {
        buffer.writeCollection(refs, (buf, ref) -> {
            buf.writeUtf(ref.manualId());
            buf.writeUtf(ref.lessonId());
            buf.writeUtf(ref.pageId());
        });
    }

    public static List<ManualReferenceSpec> readRefs(FriendlyByteBuf buffer) {
        return buffer.readList(buf -> new ManualReferenceSpec(buf.readUtf(), buf.readUtf(), buf.readUtf()));
    }
}
