package me.drex.essentials.command.impl.home;

import com.mojang.authlib.GameProfile;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import eu.pb4.placeholders.api.PlaceholderContext;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import me.drex.essentials.command.Command;
import me.drex.essentials.command.CommandProperties;
import me.drex.essentials.config.homes.HomesConfig;
import me.drex.essentials.config.homes.HomesLimit;
import me.drex.essentials.storage.DataStorage;
import me.drex.essentials.storage.PlayerData;
import me.drex.essentials.util.teleportation.Home;
import me.drex.essentials.util.teleportation.Location;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.Level;

import java.util.Map;

import static com.mojang.brigadier.arguments.StringArgumentType.getString;
import static com.mojang.brigadier.arguments.StringArgumentType.word;
import static me.drex.message.api.LocalizedMessage.localized;
import static net.minecraft.commands.Commands.argument;
import static net.minecraft.commands.Commands.literal;
import static net.minecraft.commands.arguments.GameProfileArgument.gameProfile;
import static me.drex.essentials.command.impl.home.HomeCommand.DEFAULT_HOME_NAME;
import static me.drex.essentials.command.util.CommandUtil.PROFILES_PROVIDER;
import static me.drex.essentials.command.util.CommandUtil.getGameProfile;

public class SetHomeCommand extends Command {

    public SetHomeCommand() {
        super(CommandProperties.create("sethome", 0));
    }

    @Override
    protected void registerArguments(LiteralArgumentBuilder<CommandSourceStack> literal, CommandBuildContext commandBuildContext) {
        literal
            .then(
                argument("home", word())
                    .then(argument("player", gameProfile()).suggests(PROFILES_PROVIDER)
                        .requires(require("other"))
                        .then(literal("-confirm")
                            .executes(ctx -> setHome(ctx.getSource(), getString(ctx, "home"), getGameProfile(ctx, "player"), false, true))
                        )
                        .executes(ctx -> setHome(ctx.getSource(), getString(ctx, "home"), getGameProfile(ctx, "player"), false, false))
                    ).then(literal("-confirm")
                        .executes(ctx -> setHome(ctx.getSource(), getString(ctx, "home"), ctx.getSource().getPlayerOrException().getGameProfile(), true, true))
                    )
                    .executes(ctx -> setHome(ctx.getSource(), getString(ctx, "home"), ctx.getSource().getPlayerOrException().getGameProfile(), true, false))
            )
            .executes(ctx -> setHome(ctx.getSource(), DEFAULT_HOME_NAME, ctx.getSource().getPlayerOrException().getGameProfile(), true, false));
    }

    protected int setHome(CommandSourceStack src, String name, GameProfile target, boolean self, boolean confirm) {
        if (!src.getLevel().dimension().equals(Level.OVERWORLD) && !src.getLevel().dimension().equals(Level.NETHER) && !src.getLevel().dimension().equals(Level.END) && !src.hasPermission(4)){
            src.sendFailure(Component.nullToEmpty("您無法在此維度建立 Home點"));
            return FAILURE;
        }
        PlayerData playerData = DataStorage.getOfflinePlayerData(src.getServer(), target.getId());
        Map<String, Home> homes = playerData.homes;
        Home previousHome = homes.get(name);
        if (previousHome != null && !confirm) {
            if (self) {
                src.sendFailure(localized("fabric-essentials.commands.sethome.self.confirm", previousHome.placeholders(name)));
            } else {
                src.sendFailure(localized("fabric-essentials.commands.sethome.other.confirm", previousHome.placeholders(name), PlaceholderContext.of(target, src.getServer())));
            }
            return FAILURE;
        }
        int limit = getHomesLimit(src);
        boolean overwrite = confirm && previousHome != null;
        if (homes.size() >= limit) {
            src.sendFailure(localized("fabric-essentials.commands.sethome.limit"));
            return FAILURE;
        }
        Home home = new Home(new Location(src));
        homes.put(name, home);
        DataStorage.updateOfflinePlayerData(src.getServer(), target.getId(), playerData);
        if (self) {
            src.sendSystemMessage(localized("fabric-essentials.commands.sethome.self", home.placeholders(name)));
        } else {
            src.sendSystemMessage(localized("fabric-essentials.commands.sethome.other", home.placeholders(name), PlaceholderContext.of(target, src.getServer())));
        }
        return SUCCESS;
    }

    private int getHomesLimit(CommandSourceStack src) {
        if (check(src, "limit.bypass")) {
            return Integer.MAX_VALUE;
        } else {
            HomesConfig homesConfig = config().homes;
            int limit = homesConfig.defaultLimit;
            int added = 0;
            for (HomesLimit homesLimit : homesConfig.homesLimits) {
                if (check(src, "limit." + homesLimit.permission)) {
                    if (homesLimit.stacks) {
                        added += homesLimit.limit;
                    } else {
                        limit = Math.max(limit, homesLimit.limit);
                    }
                }
            }
            limit += added;
            return limit;
        }
    }

}
