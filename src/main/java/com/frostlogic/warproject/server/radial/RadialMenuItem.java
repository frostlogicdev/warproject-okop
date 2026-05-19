package com.frostlogic.warproject.server.radial;

import com.frostlogic.warproject.attachment.PlayerState;
import com.frostlogic.warproject.attachment.Role;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;

/**
 * Available actions in the radial menu.
 * <p>
 * Each item carries the metadata required for the server-side authority check
 * performed by {@link RadialMenuDispatcher}:
 * <ul>
 *   <li>{@link #requiredRole()} — minimum {@link Role} the initiator must have;</li>
 *   <li>{@link #appliesTo(PlayerState)} — set of {@link PlayerState target states} the action is meaningful for;</li>
 *   <li>{@link #requiresOwnFaction()} — whether the target must belong to the same faction as the initiator;</li>
 *   <li>{@link #requiresEnemyFaction()} — whether the target must belong to a different faction (and both must be members);</li>
 *   <li>{@link #requiresOwnBase()} — whether the initiator must currently be inside their own faction's base region.</li>
 * </ul>
 * <p>
 * Special case: {@link #CAPTURE_PASSPORT} is the only item that requires the
 * target to belong to a <em>different</em> faction (both factions must be set
 * and they must differ). All other items either require the same faction
 * ({@link #requiresOwnFaction()}) or do not constrain factions at all.
 * <p>
 * Requirements: 9.5–9.7, 21.1
 * Design: §5.3, §12 Property 11
 */
public enum RadialMenuItem {

    /** Принять кандидата (COMMANDER, цель CANDIDATE, своя фракция, на своей базе). */
    ACCEPT(
            Role.COMMANDER,
            Set.of(PlayerState.CANDIDATE),
            TargetFactionRule.SAME,
            /*requiresOwnBase=*/ true
    ),

    /** Захватить паспорт (SOLDIER, цель ACCEPTED, вражеская фракция). */
    CAPTURE_PASSPORT(
            Role.SOLDIER,
            Set.of(PlayerState.ACCEPTED),
            TargetFactionRule.ENEMY,
            /*requiresOwnBase=*/ false
    ),

    /** Повысить в звании (COMMANDER, цель ACCEPTED, своя фракция). */
    PROMOTE(
            Role.COMMANDER,
            Set.of(PlayerState.ACCEPTED),
            TargetFactionRule.SAME,
            /*requiresOwnBase=*/ false
    ),

    /** Понизить в звании (COMMANDER, цель ACCEPTED, своя фракция). */
    DEMOTE(
            Role.COMMANDER,
            Set.of(PlayerState.ACCEPTED),
            TargetFactionRule.SAME,
            /*requiresOwnBase=*/ false
    ),

    /** Пометить коллаборантом (OP, любой статус цели, фракции не учитываются). */
    COLLAB_MARK(
            Role.OP,
            EnumSet.allOf(PlayerState.class),
            TargetFactionRule.ANY,
            /*requiresOwnBase=*/ false
    ),

    /** Снять пометку коллаборанта (OP, любой статус цели). */
    COLLAB_UNMARK(
            Role.OP,
            EnumSet.allOf(PlayerState.class),
            TargetFactionRule.ANY,
            /*requiresOwnBase=*/ false
    ),

    /** Открыть выкуп пленного (COMMANDER, цель CAPTURED). */
    RANSOM_OPEN(
            Role.COMMANDER,
            Set.of(PlayerState.CAPTURED),
            TargetFactionRule.ANY,
            /*requiresOwnBase=*/ false
    ),

    /** Пригласить в подразделение (COMMANDER, цель ACCEPTED, своя фракция). */
    SUBDIV_INVITE(
            Role.COMMANDER,
            Set.of(PlayerState.ACCEPTED),
            TargetFactionRule.SAME,
            /*requiresOwnBase=*/ false
    ),

    /** Исключить из подразделения (COMMANDER, цель ACCEPTED, своя фракция). */
    SUBDIV_KICK(
            Role.COMMANDER,
            Set.of(PlayerState.ACCEPTED),
            TargetFactionRule.SAME,
            /*requiresOwnBase=*/ false
    );

    public static final StreamCodec<ByteBuf, RadialMenuItem> STREAM_CODEC =
            ByteBufCodecs.idMapper(i -> values()[i], RadialMenuItem::ordinal);

    private final Role requiredRole;
    private final Set<PlayerState> applicableTargetStates;
    private final TargetFactionRule factionRule;
    private final boolean requiresOwnBase;

    RadialMenuItem(Role requiredRole,
                   Set<PlayerState> applicableTargetStates,
                   TargetFactionRule factionRule,
                   boolean requiresOwnBase) {
        this.requiredRole = requiredRole;
        this.applicableTargetStates = Collections.unmodifiableSet(applicableTargetStates);
        this.factionRule = factionRule;
        this.requiresOwnBase = requiresOwnBase;
    }

    /** Minimum role required to invoke this item. */
    public Role requiredRole() {
        return requiredRole;
    }

    /** Returns true if this item is meaningful when the target is in {@code state}. */
    public boolean appliesTo(PlayerState state) {
        return applicableTargetStates.contains(state);
    }

    /** Returns the (immutable) set of target states this item applies to. */
    public Set<PlayerState> applicableTargetStates() {
        return applicableTargetStates;
    }

    /**
     * Returns true if the initiator and target must belong to the <em>same</em> faction
     * (and both must have a faction set).
     */
    public boolean requiresOwnFaction() {
        return factionRule == TargetFactionRule.SAME;
    }

    /**
     * Returns true if the initiator and target must belong to <em>different</em> factions
     * (and both must have a faction set).
     * <p>
     * Currently only {@link #CAPTURE_PASSPORT} sets this flag.
     */
    public boolean requiresEnemyFaction() {
        return factionRule == TargetFactionRule.ENEMY;
    }

    /**
     * Returns true if the initiator must be physically located inside their own
     * faction's base region (cf. design §8.3, §12 Property 11).
     */
    public boolean requiresOwnBase() {
        return requiresOwnBase;
    }

    /**
     * Faction relationship rule between initiator and target.
     */
    private enum TargetFactionRule {
        /** No constraint on factions (e.g. OP-only items). */
        ANY,
        /** Initiator and target must have the same faction (both must be members). */
        SAME,
        /** Initiator and target must have different factions (both must be members). */
        ENEMY
    }
}
