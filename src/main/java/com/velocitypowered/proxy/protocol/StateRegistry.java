package com.velocitypowered.proxy.protocol;

import com.velocitypowered.proxy.protocol.packets.*;
import io.netty.util.collection.IntObjectHashMap;
import io.netty.util.collection.IntObjectMap;

import java.util.*;
import java.util.function.Supplier;

import static com.velocitypowered.proxy.protocol.ProtocolConstants.*;

public enum StateRegistry {
    HANDSHAKE {
        {
            SERVERBOUND.register(Handshake.class, Handshake::new,
                    genericMapping(0x00));
        }
    },
    STATUS {
        {
            SERVERBOUND.register(StatusRequest.class, StatusRequest::new,
                    genericMapping(0x00));
            SERVERBOUND.register(StatusPing.class, StatusPing::new,
                    genericMapping(0x01));

            CLIENTBOUND.register(StatusResponse.class, StatusResponse::new,
                    genericMapping(0x00));
            CLIENTBOUND.register(StatusPing.class, StatusPing::new,
                    genericMapping(0x01));
        }
    },
    PLAY {
        {
            SERVERBOUND.register(Chat.class, Chat::new,
                    map(0x02, MINECRAFT_1_11),
                    map(0x03, MINECRAFT_1_12),
                    map(0x02, MINECRAFT_1_12_2));
            SERVERBOUND.register(PluginMessage.class, PluginMessage::new,
                    map(0x09, MINECRAFT_1_11),
                    map(0x0A, MINECRAFT_1_12),
                    map(0x09, MINECRAFT_1_12_1));
            SERVERBOUND.register(KeepAlive.class, KeepAlive::new,
                    map(0x0B, MINECRAFT_1_11),
                    map(0x0C, MINECRAFT_1_12),
                    map(0x0B, MINECRAFT_1_12_1));
            SERVERBOUND.register(ClientSettings.class, ClientSettings::new,
                    map(0x04, MINECRAFT_1_11),
                    map(0x05, MINECRAFT_1_12),
                    map(0x04, MINECRAFT_1_12_1));

            CLIENTBOUND.register(BossBar.class, BossBar::new,
                    map(0x0C, MINECRAFT_1_11));
            CLIENTBOUND.register(Chat.class, Chat::new,
                    map(0x0F, MINECRAFT_1_11));
            CLIENTBOUND.register(PluginMessage.class, PluginMessage::new,
                    map(0x18, MINECRAFT_1_11));
            CLIENTBOUND.register(Disconnect.class, Disconnect::new,
                    map(0x1A, MINECRAFT_1_11));
            CLIENTBOUND.register(KeepAlive.class, KeepAlive::new,
                    map(0x1F, MINECRAFT_1_11));
            CLIENTBOUND.register(JoinGame.class, JoinGame::new,
                    map(0x23, MINECRAFT_1_11));
            CLIENTBOUND.register(Respawn.class, Respawn::new,
                    map(0x33, MINECRAFT_1_11),
                    map(0x34, MINECRAFT_1_12),
                    map(0x35, MINECRAFT_1_12_2));
            CLIENTBOUND.register(ScoreboardDisplay.class, ScoreboardDisplay::new,
                    map(0x3B, MINECRAFT_1_12_2));
            CLIENTBOUND.register(ScoreboardObjective.class, ScoreboardObjective::new,
                    map(0x42, MINECRAFT_1_12_2));
            CLIENTBOUND.register(ScoreboardTeam.class, ScoreboardTeam::new,
                    map(0x44, MINECRAFT_1_12_2));
            CLIENTBOUND.register(ScoreboardSetScore.class, ScoreboardSetScore::new,
                    map(0x45, MINECRAFT_1_12_2));
        }
    },
    LOGIN {
        {
            SERVERBOUND.register(ServerLogin.class, ServerLogin::new,
                    genericMapping(0x00));
            SERVERBOUND.register(EncryptionResponse.class, EncryptionResponse::new,
                    genericMapping(0x01));

            CLIENTBOUND.register(Disconnect.class, Disconnect::new,
                    genericMapping(0x00));
            CLIENTBOUND.register(EncryptionRequest.class, EncryptionRequest::new,
                    genericMapping(0x01));
            CLIENTBOUND.register(ServerLoginSuccess.class, ServerLoginSuccess::new,
                    genericMapping(0x02));
            CLIENTBOUND.register(SetCompression.class, SetCompression::new,
                    genericMapping(0x03));
        }
    };

    public final PacketRegistry CLIENTBOUND = new PacketRegistry(ProtocolConstants.Direction.CLIENTBOUND);
    public final PacketRegistry SERVERBOUND = new PacketRegistry(ProtocolConstants.Direction.SERVERBOUND);

    public static class PacketRegistry {
        private static final IntObjectMap<int[]> LINKED_PROTOCOL_VERSIONS = new IntObjectHashMap<>();

        static {
            LINKED_PROTOCOL_VERSIONS.put(MINECRAFT_1_11, new int[] { MINECRAFT_1_11_1, MINECRAFT_1_12 });
            LINKED_PROTOCOL_VERSIONS.put(MINECRAFT_1_12, new int[] { MINECRAFT_1_12_1 });
            LINKED_PROTOCOL_VERSIONS.put(MINECRAFT_1_12_1, new int[] { MINECRAFT_1_12_2 });
        }

        private final ProtocolConstants.Direction direction;
        private final IntObjectMap<ProtocolVersion> versions = new IntObjectHashMap<>();

        public PacketRegistry(ProtocolConstants.Direction direction) {
            this.direction = direction;
            for (int version : ProtocolConstants.SUPPORTED_VERSIONS) {
                versions.put(version, new ProtocolVersion(version));
            }
            versions.put(MINIMUM_GENERIC_VERSION, new ProtocolVersion(MINIMUM_GENERIC_VERSION));
        }

        public ProtocolVersion getVersion(final int version) {
            ProtocolVersion result = versions.get(version);
            if (result == null) {
                throw new IllegalArgumentException("Could not find data for protocol version " + version);
            }
            return result;
        }

        public <P extends MinecraftPacket> void register(Class<P> clazz, Supplier<P> packetSupplier, PacketMapping... mappings) {
            if (mappings.length == 0) {
                throw new IllegalArgumentException("At least one mapping must be provided.");
            }

            for (final PacketMapping mapping : mappings) {
                ProtocolVersion version = this.versions.get(mapping.protocolVersion);
                if (version == null) {
                    throw new IllegalArgumentException("Unknown protocol version " + mapping.protocolVersion);
                }
                version.packetIdToSupplier.put(mapping.id, packetSupplier);
                version.packetClassToId.put(clazz, mapping.id);

                int[] linked = LINKED_PROTOCOL_VERSIONS.get(mapping.protocolVersion);
                if (linked != null) {
                    links: for (int i : linked) {
                        // Make sure that later mappings override this one.
                        for (PacketMapping m : mappings) {
                            if (i == m.protocolVersion) continue links;
                        }
                        register(clazz, packetSupplier, map(mapping.id, i));
                    }
                }
            }
        }

        public class ProtocolVersion {
            public final int id;
            final IntObjectMap<Supplier<? extends MinecraftPacket>> packetIdToSupplier = new IntObjectHashMap<>();
            final Map<Class<? extends MinecraftPacket>, Integer> packetClassToId = new HashMap<>();

            ProtocolVersion(final int id) {
                this.id = id;
            }

            public MinecraftPacket createPacket(final int id) {
                final Supplier<? extends MinecraftPacket> supplier = this.packetIdToSupplier.get(id);
                if (supplier == null) {
                    return null;
                }
                return supplier.get();
            }

            public int getPacketId(final MinecraftPacket packet) {
                final Integer id = this.packetClassToId.get(packet.getClass());
                if (id == null) {
                    throw new IllegalArgumentException(String.format(
                            "Unable to find id for packet of type %s in %s protocol %s",
                            packet.getClass().getName(), PacketRegistry.this.direction, this.id
                    ));
                }
                return id;
            }

            @Override
            public String toString() {
                return "ProtocolVersion{" +
                        "id=" + id +
                        ", packetClassToId=" + packetClassToId +
                        '}';
            }
        }
    }

    public static class PacketMapping {
        private final int id;
        private final int protocolVersion;

        public PacketMapping(int id, int protocolVersion) {
            this.id = id;
            this.protocolVersion = protocolVersion;
        }

        @Override
        public String toString() {
            return "PacketMapping{" +
                    "id=" + id +
                    ", protocolVersion=" + protocolVersion +
                    '}';
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            PacketMapping that = (PacketMapping) o;
            return id == that.id &&
                    protocolVersion == that.protocolVersion;
        }

        @Override
        public int hashCode() {
            return Objects.hash(id, protocolVersion);
        }
    }

    private static PacketMapping map(int id, int version) {
        return new PacketMapping(id, version);
    }

    private static PacketMapping genericMapping(int id) {
        return new PacketMapping(id, MINIMUM_GENERIC_VERSION);
    }
}
