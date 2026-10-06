package io.github.skystrike.shared.command;

import io.github.skystrike.shared.config.PlayerConfig;
import java.util.List;

/**
 * The declarative half of the server's command set: names, aliases, descriptions, arguments and
 * permissions — no behaviour.
 *
 * <p>This is part of the client–server contract, which is why it lives in {@code shared}: the
 * server attaches handlers and executes, while the client registers the same metadata
 * handler-less so {@code help}, the hint line and tab completion describe the commands it will
 * forward. Two tables would drift; one table cannot. The wire itself never carries these specs —
 * a client could ship none of them and still send any raw line, which the server then authorises
 * against its <i>own</i> registry.
 */
public final class ServerCommandCatalog {

    private ServerCommandCatalog() {
    }

    /** Every server-executable command's metadata, in stable order. */
    public static List<CommandSpec> metadata() {
        return List.of(
            CommandSpec.builder("say")
                .description("broadcast a server announcement to everyone")
                .permission(Permission.MODERATOR)
                .side(CommandSide.SERVER)
                .arg("message", ArgTypes.GREEDY_STRING, "the announcement text")
                .build(),
            CommandSpec.builder("respawn")
                .description("force-respawn yourself, or another player")
                .permission(Permission.MODERATOR)
                .side(CommandSide.SERVER)
                .optionalArg("player", ArgTypes.PLAYER, "defaults to you")
                .build(),
            CommandSpec.builder("kill")
                .description("kill yourself, or another player")
                .permission(Permission.MODERATOR)
                .side(CommandSide.SERVER)
                .optionalArg("player", ArgTypes.PLAYER, "defaults to you")
                .build(),
            CommandSpec.builder("teleport")
                .description("move a player to arena coordinates")
                .alias("tp")
                .permission(Permission.MODERATOR)
                .side(CommandSide.SERVER)
                .arg("x", ArgTypes.FLOAT(0f, 3000f), "target x in world units")
                .arg("y", ArgTypes.FLOAT(0f, 2000f), "target y in world units")
                .optionalArg("player", ArgTypes.PLAYER, "defaults to you")
                .build(),
            CommandSpec.builder("give")
                .description("set a player's primary weapon, applied immediately")
                .permission(Permission.MODERATOR)
                .side(CommandSide.SERVER)
                .arg("weapon", ArgTypes.WEAPON, "weapon id, e.g. iron_carbine")
                .optionalArg("player", ArgTypes.PLAYER, "defaults to you")
                .build(),
            CommandSpec.builder("setammo")
                .description("set magazine and reserve rounds of the held gun")
                .permission(Permission.MODERATOR)
                .side(CommandSide.SERVER)
                .arg("magazine", ArgTypes.INT(0, 500), "rounds in the magazine")
                .optionalArg("reserve", ArgTypes.INT(0, 4000), "rounds in reserve; kept when omitted")
                .optionalArg("player", ArgTypes.PLAYER, "defaults to you")
                .build(),
            CommandSpec.builder("sethealth")
                .description("set a player's health points")
                .permission(Permission.MODERATOR)
                .side(CommandSide.SERVER)
                .arg("hp", ArgTypes.INT(1, (int) PlayerConfig.MAX_HEALTH), "health points")
                .optionalArg("player", ArgTypes.PLAYER, "defaults to you")
                .build(),
            CommandSpec.builder("setteam")
                .description("move a player to another team and respawn them")
                .permission(Permission.MODERATOR)
                .side(CommandSide.SERVER)
                .arg("team", ArgTypes.TEAM, "a, b or neutral")
                .optionalArg("player", ArgTypes.PLAYER, "defaults to you")
                .build(),
            CommandSpec.builder("kick")
                .description("disconnect a player from the match")
                .permission(Permission.MODERATOR)
                .side(CommandSide.SERVER)
                .arg("player", ArgTypes.PLAYER, "who to kick")
                .optionalArg("reason", ArgTypes.GREEDY_STRING, "shown before disconnecting")
                .build(),
            CommandSpec.builder("noclip")
                .description("fly through terrain (simulation reads it from milestone M3)")
                .permission(Permission.ADMIN)
                .side(CommandSide.SERVER)
                .optionalArg("state", ArgTypes.BOOL, "on/off; omitted toggles")
                .optionalArg("player", ArgTypes.PLAYER, "defaults to you")
                .build(),
            CommandSpec.builder("godmode")
                .description("ignore incoming damage (simulation reads it from milestone M3)")
                .permission(Permission.ADMIN)
                .side(CommandSide.SERVER)
                .optionalArg("state", ArgTypes.BOOL, "on/off; omitted toggles")
                .optionalArg("player", ArgTypes.PLAYER, "defaults to you")
                .build(),
            CommandSpec.builder("infiniteammo")
                .description("never spend magazine or reserve rounds (simulation reads it from milestone M3)")
                .permission(Permission.ADMIN)
                .side(CommandSide.SERVER)
                .optionalArg("state", ArgTypes.BOOL, "on/off; omitted toggles")
                .optionalArg("player", ArgTypes.PLAYER, "defaults to you")
                .build());
    }
}
