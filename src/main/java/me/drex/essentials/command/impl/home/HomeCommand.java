package me.drex.essentials.command.impl.home;

import com.mojang.authlib.GameProfile;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import eu.pb4.placeholders.api.PlaceholderContext;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import me.drex.essentials.command.Command;
import me.drex.essentials.command.CommandProperties;
import me.drex.essentials.command.util.CommandUtil;
import me.drex.essentials.storage.DataStorage;
import me.drex.essentials.storage.PlayerData;
import me.drex.essentials.util.teleportation.Home;

import static com.mojang.brigadier.arguments.StringArgumentType.getString;
import static com.mojang.brigadier.arguments.StringArgumentType.word;
import static me.drex.message.api.LocalizedMessage.localized;
import static net.minecraft.commands.Commands.argument;
import static net.minecraft.commands.arguments.GameProfileArgument.gameProfile;
import static me.drex.essentials.command.util.CommandUtil.PROFILES_PROVIDER;
import static me.drex.essentials.command.util.CommandUtil.getGameProfile;

public class HomeCommand extends Command {

    public static final SimpleCommandExceptionType UNKNOWN = new SimpleCommandExceptionType(localized("fabric-essentials.commands.home.unknown"));
    public static final String DEFAULT_HOME_NAME = "home";

    public HomeCommand() {
        super(CommandProperties.create("home", 0));
    }

    @Override
    protected void registerArguments(LiteralArgumentBuilder<CommandSourceStack> literal, CommandBuildContext commandBuildContext) {
        literal
                .then(
                        argument("home", word()).suggests(HOMES_PROVIDER)
                                .then(argument("player", gameProfile()).suggests(PROFILES_PROVIDER)
                                        .requires(require("other"))
                                        .executes(ctx -> teleportHome(ctx.getSource(), getString(ctx, "home"), getGameProfile(ctx, "player"), false))
                                )
                                .executes(ctx -> teleportHome(ctx.getSource(), getString(ctx, "home"), ctx.getSource().getPlayerOrException().getGameProfile(), true))
                )
                .executes(ctx -> teleportHome(ctx.getSource(), DEFAULT_HOME_NAME, ctx.getSource().getPlayerOrException().getGameProfile(), true));
    }

    protected int teleportHome(CommandSourceStack src, String name, GameProfile target, boolean self) throws CommandSyntaxException {
        ServerPlayer serverPlayer = src.getPlayerOrException();
        PlayerData playerData = DataStorage.getOfflinePlayerData(src.getServer(), target.getId());
        Home home = playerData.homes.get(name);
        if (home == null) throw UNKNOWN.create();
        ServerLevel targetLevel = home.location().getLevel(src.getServer());
        if (targetLevel != null) {
            CommandUtil.asyncTeleport(src, targetLevel, home.location().chunkPos(), config().teleportation.waitingPeriod).whenCompleteAsync((chunkAccess, throwable) -> {
                if (chunkAccess == null) return;
                if (self) {
                    src.sendSystemMessage(localized("fabric-essentials.commands.home.self", home.placeholders(name)));
                } else {
                    src.sendSystemMessage(localized("fabric-essentials.commands.home.other", home.placeholders(name), PlaceholderContext.of(target, src.getServer())));
                }
                home.location().teleport(serverPlayer);
            }, src.getServer());
            return SUCCESS;
        } else {
            throw WORLD_UNKNOWN.create();
        }
    }

    public static final SuggestionProvider<CommandSourceStack> HOMES_PROVIDER = (ctx, builder) -> {
        PlayerData playerData = DataStorage.getPlayerData(ctx);
        return SharedSuggestionProvider.suggest(playerData.homes.keySet(), builder);
    };

    public static final SuggestionProvider<CommandSourceStack> OTHER_HOMES_PROVIDER = (ctx, builder) -> {
        PlayerData playerData = DataStorage.getOfflinePlayerData(ctx.getSource().getServer(), getGameProfile(ctx, "player").getId());
        return SharedSuggestionProvider.suggest(playerData.homes.keySet(), builder);
    };

}
