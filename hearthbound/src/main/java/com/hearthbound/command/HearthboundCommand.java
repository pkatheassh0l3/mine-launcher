package com.hearthbound.command;

import com.hearthbound.data.Culture;
import com.hearthbound.data.HBData;
import com.hearthbound.registry.ModRegistry;
import com.hearthbound.rpg.PlayerData;
import com.hearthbound.rpg.Rpg;
import com.hearthbound.village.Contracts;
import com.hearthbound.village.Reputation;
import com.hearthbound.village.Village;
import com.hearthbound.village.VillageData;
import com.hearthbound.village.VillageManager;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.ResourceLocationArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.levelgen.Heightmap;

import java.util.Collection;

/** {@code /hearthbound} – admin tools for villages and characters. */
public final class HearthboundCommand {
    private static final SuggestionProvider<CommandSourceStack> CULTURES = (ctx, b) ->
            SharedSuggestionProvider.suggestResource(HBData.cultureMap().keySet(), b);

    private HearthboundCommand() {}

    public static void register(CommandDispatcher<CommandSourceStack> d) {
        d.register(Commands.literal("hearthbound")
                .then(Commands.literal("villages").executes(HearthboundCommand::list))
                .then(Commands.literal("village").requires(s -> s.hasPermission(2))
                        .then(Commands.literal("info").executes(HearthboundCommand::info))
                        .then(Commands.literal("create")
                                .then(Commands.argument("culture", ResourceLocationArgument.id()).suggests(CULTURES)
                                        .executes(HearthboundCommand::create)))
                        .then(Commands.literal("grow").executes(HearthboundCommand::grow))
                        .then(Commands.literal("raid").executes(HearthboundCommand::raid)
                                .then(Commands.literal("pillagers").executes(HearthboundCommand::pillagerRaid)))
                        .then(Commands.literal("board").executes(HearthboundCommand::board))
                        .then(Commands.literal("forget").executes(HearthboundCommand::forget))
                        .then(Commands.literal("diplomacy").executes(HearthboundCommand::diplomacy))
                        .then(Commands.literal("war").executes(HearthboundCommand::war))
                        .then(Commands.literal("attack").executes(HearthboundCommand::attack))
                        .then(Commands.literal("caravan").executes(HearthboundCommand::caravan)))
                .then(Commands.literal("rep").requires(s -> s.hasPermission(2))
                        .then(Commands.argument("players", EntityArgument.players())
                                .then(Commands.argument("amount", IntegerArgumentType.integer())
                                        .executes(HearthboundCommand::rep))))
                .then(Commands.literal("xp").requires(s -> s.hasPermission(2))
                        .then(Commands.argument("players", EntityArgument.players())
                                .then(Commands.argument("amount", IntegerArgumentType.integer(1))
                                        .executes(HearthboundCommand::xp))))
                .then(Commands.literal("points").requires(s -> s.hasPermission(2))
                        .then(Commands.argument("players", EntityArgument.players())
                                .then(Commands.argument("amount", IntegerArgumentType.integer())
                                        .executes(HearthboundCommand::points))))
                .then(Commands.literal("reset").requires(s -> s.hasPermission(2))
                        .then(Commands.argument("players", EntityArgument.players())
                                .executes(HearthboundCommand::reset))));
    }

    private static Village nearest(CommandSourceStack src) {
        ServerLevel level = src.getLevel();
        return VillageData.get(src.getServer()).nearest(level, BlockPos.containing(src.getPosition()), 512);
    }

    private static int noVillage(CommandSourceStack src) {
        src.sendFailure(Component.translatable("hearthbound.cmd.none"));
        return 0;
    }

    private static int list(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack src = ctx.getSource();
        Collection<Village> all = VillageData.get(src.getServer()).all();
        src.sendSuccess(() -> Component.translatable("hearthbound.cmd.list", all.size()), false);
        for (Village v : all) {
            Culture c = v.culture();
            src.sendSuccess(() -> Component.literal(" • ").append(Component.literal(v.name).withColor(c == null ? 0xFFFFFF : c.color & 0xFFFFFF))
                    .append(Component.literal(" [" + v.dimension.location().getPath() + " " + v.center.getX() + ", " + v.center.getY() + ", " + v.center.getZ() + "] "))
                    .append(Component.translatable("hearthbound.tier." + v.tier())), false);
        }
        return all.size();
    }

    private static int info(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack src = ctx.getSource();
        Village v = nearest(src);
        if (v == null) return noVillage(src);
        src.sendSuccess(() -> Component.literal(v.name + " — " + v.culture + ", tier " + v.tier() + ", pop " + v.population() + "/" + v.housing()
                + ", buildings " + v.completedBuildings() + "/" + v.buildings.size() + ", stock " + v.stock + ", prosperity " + v.prosperity), false);
        return 1;
    }

    private static int create(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        CommandSourceStack src = ctx.getSource();
        ResourceLocation id = ResourceLocationArgument.getId(ctx, "culture");
        Culture c = HBData.cultureMap().get(id);
        if (c == null) {
            src.sendFailure(Component.translatable("hearthbound.cmd.unknown_culture", id.toString()));
            return 0;
        }
        ServerLevel level = src.getLevel();
        BlockPos p = BlockPos.containing(src.getPosition());
        int y = com.hearthbound.village.Terraform.groundTop(level, p.getX(), p.getZ()) + 1;
        Village v = VillageManager.create(level, c, new BlockPos(p.getX(), y, p.getZ()), true, null);
        src.sendSuccess(() -> Component.translatable("hearthbound.cmd.created", v.name), true);
        return 1;
    }

    private static int grow(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack src = ctx.getSource();
        Village v = nearest(src);
        if (v == null) return noVillage(src);
        ServerLevel level = src.getServer().getLevel(v.dimension);
        if (level == null) return 0;
        VillageManager.finishNow(level, v);
        VillageData.get(src.getServer()).setDirty();
        src.sendSuccess(() -> Component.translatable("hearthbound.cmd.grown", v.name), true);
        return 1;
    }

    private static int raid(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack src = ctx.getSource();
        Village v = nearest(src);
        if (v == null) return noVillage(src);
        ServerLevel level = src.getServer().getLevel(v.dimension);
        if (level == null) return 0;
        VillageManager.startRaid(level, v);
        return 1;
    }

    private static int pillagerRaid(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack src = ctx.getSource();
        Village v = nearest(src);
        if (v == null) return noVillage(src);
        ServerLevel level = src.getServer().getLevel(v.dimension);
        if (level == null) return 0;
        VillageManager.startPillagerRaid(level, v);
        return 1;
    }

    private static int board(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack src = ctx.getSource();
        Village v = nearest(src);
        if (v == null) return noVillage(src);
        ServerLevel level = src.getServer().getLevel(v.dimension);
        if (level == null) return 0;
        Contracts.refreshBoard(level, v, true);
        VillageData.get(src.getServer()).setDirty();
        src.sendSuccess(() -> Component.translatable("hearthbound.cmd.board", v.name), true);
        return 1;
    }

    private static int forget(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack src = ctx.getSource();
        Village v = nearest(src);
        if (v == null) return noVillage(src);
        ServerLevel level = src.getServer().getLevel(v.dimension);
        if (level != null && level.getBlockState(v.center).is(ModRegistry.VILLAGE_HEARTH.get())) {
            level.removeBlock(v.center, false);
        }
        VillageData.get(src.getServer()).remove(v.id);
        src.sendSuccess(() -> Component.translatable("hearthbound.cmd.forgot", v.name), true);
        return 1;
    }

    private static com.hearthbound.village.Village closestNeighbour(VillageData vd, com.hearthbound.village.Village v) {
        com.hearthbound.village.Village best = null;
        for (var o : com.hearthbound.village.Diplomacy.neighbours(vd, v)) {
            if (best == null || o.center.distSqr(v.center) < best.center.distSqr(v.center)) best = o;
        }
        return best;
    }

    private static int diplomacy(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack src = ctx.getSource();
        Village v = nearest(src);
        if (v == null) return noVillage(src);
        VillageData vd = VillageData.get(src.getServer());
        for (var o : com.hearthbound.village.Diplomacy.neighbours(vd, v)) {
            var rel = com.hearthbound.village.Diplomacy.relation(vd, v, o);
            var st = com.hearthbound.village.Diplomacy.stance(rel);
            src.sendSuccess(() -> Component.literal(" • " + o.name + ": " + rel.value + " ").append(com.hearthbound.village.Diplomacy.stanceName(st)), false);
        }
        return 1;
    }

    private static int war(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack src = ctx.getSource();
        Village v = nearest(src);
        if (v == null) return noVillage(src);
        VillageData vd = VillageData.get(src.getServer());
        Village o = closestNeighbour(vd, v);
        if (o == null) return noVillage(src);
        var rel = com.hearthbound.village.Diplomacy.relation(vd, v, o);
        if (rel.atWar()) com.hearthbound.village.Diplomacy.makePeace(src.getServer(), vd, v, o, src.getLevel().getDayTime() / 24000L, null);
        else com.hearthbound.village.Diplomacy.declareWar(src.getServer(), vd, v, o, src.getLevel().getDayTime() / 24000L);
        return 1;
    }

    private static int attack(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack src = ctx.getSource();
        Village v = nearest(src);
        if (v == null) return noVillage(src);
        VillageData vd = VillageData.get(src.getServer());
        Village o = closestNeighbour(vd, v);
        if (o == null) return noVillage(src);
        com.hearthbound.village.Diplomacy.launch(src.getServer(), vd, o, v, src.getLevel().getGameTime());
        return 1;
    }

    private static int caravan(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack src = ctx.getSource();
        Village v = nearest(src);
        if (v == null) return noVillage(src);
        com.hearthbound.village.Diplomacy.sendCaravan(src.getServer(), VillageData.get(src.getServer()), v, src.getLevel().getGameTime());
        return 1;
    }

    private static int rep(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        CommandSourceStack src = ctx.getSource();
        Village v = nearest(src);
        if (v == null) return noVillage(src);
        int amount = IntegerArgumentType.getInteger(ctx, "amount");
        for (ServerPlayer p : EntityArgument.getPlayers(ctx, "players")) {
            Reputation.add(p, v, amount, false, true);
            Rpg.sync(p);
        }
        return 1;
    }

    private static int xp(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        int amount = IntegerArgumentType.getInteger(ctx, "amount");
        for (ServerPlayer p : EntityArgument.getPlayers(ctx, "players")) Rpg.addXp(p, amount, true);
        return 1;
    }

    private static int points(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        int amount = IntegerArgumentType.getInteger(ctx, "amount");
        for (ServerPlayer p : EntityArgument.getPlayers(ctx, "players")) {
            PlayerData d = Rpg.data(p);
            d.skillPoints = Math.max(0, d.skillPoints + amount);
            Rpg.sync(p);
        }
        return 1;
    }

    private static int reset(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        for (ServerPlayer p : EntityArgument.getPlayers(ctx, "players")) {
            Rpg.data(p).load(new PlayerData().save());
            Rpg.applyAttributes(p);
            Rpg.sync(p);
        }
        ctx.getSource().sendSuccess(() -> Component.translatable("hearthbound.cmd.reset"), true);
        return 1;
    }
}
