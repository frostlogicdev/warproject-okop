package com.frostlogic.warproject.network;

import com.frostlogic.warproject.attachment.FactionId;
import com.frostlogic.warproject.attachment.PlayerState;
import com.frostlogic.warproject.attachment.Role;
import com.frostlogic.warproject.network.payload.AuthMode;
import com.frostlogic.warproject.network.payload.PublicView;
import com.frostlogic.warproject.network.payload.c2s.*;
import com.frostlogic.warproject.network.payload.s2c.*;
import com.frostlogic.warproject.server.passport.PassportData;
import com.frostlogic.warproject.server.radial.RadialMenuItem;
import io.netty.buffer.Unpooled;
import net.jqwik.api.*;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Smoke tests and property-based round-trip tests for the WarProject network layer.
 * <p>
 * Smoke: verifies all TYPE constants are non-null and have correct ResourceLocations.
 * PBT: for arbitrary payloads, STREAM_CODEC.decode(STREAM_CODEC.encode(buf, p)) == p (round-trip).
 * <p>
 * <b>Validates: Requirements 4.1, 4.2</b>
 * <p>
 * Design: §15.4
 */
class PayloadCodecProperties {

    // ==================== SMOKE TESTS ====================

    @Test
    void allC2sTypesAreNonNullAndHaveCorrectNamespace() {
        assertType(RegisterRequestPayload.TYPE, "warproject", "register_request");
        assertType(LoginRequestPayload.TYPE, "warproject", "login_request");
        assertType(RadialOpenRequestPayload.TYPE, "warproject", "radial_open_request");
        assertType(RadialActionPayload.TYPE, "warproject", "radial_action");
        assertType(PassportOpenPayload.TYPE, "warproject", "passport_open");
        assertType(RpNameSubmitPayload.TYPE, "warproject", "rp_name_submit");
        assertType(CaptchaSubmitPayload.TYPE, "warproject", "captcha_submit");
        assertType(FactionChoicePayload.TYPE, "warproject", "faction_choice");
    }

    @Test
    void allS2cTypesAreNonNullAndHaveCorrectNamespace() {
        assertType(AuthScreenStatePayload.TYPE, "warproject", "auth_screen_state");
        assertType(CaptchaStatePayload.TYPE, "warproject", "captcha_state");
        assertType(RadialMenuPayload.TYPE, "warproject", "radial_menu");
        assertType(PassportSnapshotPayload.TYPE, "warproject", "passport_snapshot");
        assertType(PlayerPublicViewPayload.TYPE, "warproject", "player_public_view");
        assertType(AuthErrorPayload.TYPE, "warproject", "auth_error");
        assertType(OpenFactionChoicePayload.TYPE, "warproject", "open_faction_choice");
        assertType(OpenPassportPayload.TYPE, "warproject", "open_passport");
    }

    private void assertType(Object type, String expectedNamespace, String expectedPath) {
        assertThat(type).isNotNull();
        // CustomPacketPayload.Type has an id() method returning ResourceLocation
        var payloadType = (net.minecraft.network.protocol.common.custom.CustomPacketPayload.Type<?>) type;
        ResourceLocation id = payloadType.id();
        assertThat(id.getNamespace()).isEqualTo(expectedNamespace);
        assertThat(id.getPath()).isEqualTo(expectedPath);
    }

    // ==================== PBT ROUND-TRIP TESTS ====================

    // --- C2S Payloads ---

    @Property
    void registerRequestRoundTrip(@ForAll("registerRequests") RegisterRequestPayload payload) {
        assertRoundTrip(RegisterRequestPayload.STREAM_CODEC, payload);
    }

    @Property
    void loginRequestRoundTrip(@ForAll("loginRequests") LoginRequestPayload payload) {
        assertRoundTrip(LoginRequestPayload.STREAM_CODEC, payload);
    }

    @Property
    void radialOpenRequestRoundTrip(@ForAll("radialOpenRequests") RadialOpenRequestPayload payload) {
        assertRoundTrip(RadialOpenRequestPayload.STREAM_CODEC, payload);
    }

    @Property
    void radialActionRoundTrip(@ForAll("radialActions") RadialActionPayload payload) {
        assertRoundTrip(RadialActionPayload.STREAM_CODEC, payload);
    }

    @Property
    void passportOpenRoundTrip(@ForAll("passportOpens") PassportOpenPayload payload) {
        assertRoundTrip(PassportOpenPayload.STREAM_CODEC, payload);
    }

    @Property
    void rpNameSubmitRoundTrip(@ForAll("rpNameSubmits") RpNameSubmitPayload payload) {
        assertRoundTrip(RpNameSubmitPayload.STREAM_CODEC, payload);
    }

    @Property
    void captchaSubmitRoundTrip(@ForAll("captchaSubmits") CaptchaSubmitPayload payload) {
        assertRoundTrip(CaptchaSubmitPayload.STREAM_CODEC, payload);
    }

    @Property
    void factionChoiceRoundTrip(@ForAll("factionChoices") FactionChoicePayload payload) {
        assertRoundTrip(FactionChoicePayload.STREAM_CODEC, payload);
    }

    // --- S2C Payloads ---

    @Property
    void authScreenStateRoundTrip(@ForAll("authScreenStates") AuthScreenStatePayload payload) {
        assertRoundTrip(AuthScreenStatePayload.STREAM_CODEC, payload);
    }

    @Property
    void captchaStateRoundTrip(@ForAll("captchaStates") CaptchaStatePayload payload) {
        assertRoundTrip(CaptchaStatePayload.STREAM_CODEC, payload);
    }

    @Property
    void radialMenuRoundTrip(@ForAll("radialMenus") RadialMenuPayload payload) {
        assertRoundTrip(RadialMenuPayload.STREAM_CODEC, payload);
    }

    @Property
    void passportSnapshotRoundTrip(@ForAll("passportSnapshots") PassportSnapshotPayload payload) {
        assertRoundTrip(PassportSnapshotPayload.STREAM_CODEC, payload);
    }

    @Property
    void playerPublicViewRoundTrip(@ForAll("playerPublicViews") PlayerPublicViewPayload payload) {
        assertRoundTrip(PlayerPublicViewPayload.STREAM_CODEC, payload);
    }

    @Property
    void authErrorRoundTrip(@ForAll("authErrors") AuthErrorPayload payload) {
        assertRoundTrip(AuthErrorPayload.STREAM_CODEC, payload);
    }

    @Property
    void openFactionChoiceRoundTrip(@ForAll("openFactionChoices") OpenFactionChoicePayload payload) {
        assertRoundTrip(OpenFactionChoicePayload.STREAM_CODEC, payload);
    }

    @Property
    void openPassportRoundTrip(@ForAll("openPassports") OpenPassportPayload payload) {
        assertRoundTrip(OpenPassportPayload.STREAM_CODEC, payload);
    }

    // ==================== GENERATORS ====================

    @Provide
    Arbitrary<RegisterRequestPayload> registerRequests() {
        return Combinators.combine(
                safeStrings(1, 64),
                safeStrings(1, 64)
        ).as(RegisterRequestPayload::new);
    }

    @Provide
    Arbitrary<LoginRequestPayload> loginRequests() {
        return safeStrings(1, 64).map(LoginRequestPayload::new);
    }

    @Provide
    Arbitrary<RadialOpenRequestPayload> radialOpenRequests() {
        return Combinators.combine(
                uuids(),
                Arbitraries.floats().between(-180f, 180f),
                Arbitraries.floats().between(-90f, 90f)
        ).as(RadialOpenRequestPayload::new);
    }

    @Provide
    Arbitrary<RadialActionPayload> radialActions() {
        return Combinators.combine(
                uuids(),
                Arbitraries.of(RadialMenuItem.values())
        ).as(RadialActionPayload::new);
    }

    @Provide
    Arbitrary<PassportOpenPayload> passportOpens() {
        return Arbitraries.integers().between(0, 45).map(PassportOpenPayload::new);
    }

    @Provide
    Arbitrary<RpNameSubmitPayload> rpNameSubmits() {
        return Combinators.combine(
                safeStrings(1, 32),
                safeStrings(1, 32)
        ).as(RpNameSubmitPayload::new);
    }

    @Provide
    Arbitrary<CaptchaSubmitPayload> captchaSubmits() {
        return safeStrings(1, 16).map(CaptchaSubmitPayload::new);
    }

    @Provide
    Arbitrary<FactionChoicePayload> factionChoices() {
        return Arbitraries.of("ZARNAVIA", "CHERNOGRYAD").map(FactionChoicePayload::new);
    }

    @Provide
    Arbitrary<AuthScreenStatePayload> authScreenStates() {
        return Combinators.combine(
                Arbitraries.of(AuthMode.values()),
                safeStrings(1, 128).injectNull(0.3)
        ).as(AuthScreenStatePayload::new);
    }

    @Provide
    Arbitrary<CaptchaStatePayload> captchaStates() {
        return Combinators.combine(
                safeStrings(1, 16),
                Arbitraries.integers().between(0, 180),
                Arbitraries.integers().between(0, 3)
        ).as(CaptchaStatePayload::new);
    }

    @Provide
    Arbitrary<RadialMenuPayload> radialMenus() {
        return Combinators.combine(
                uuids(),
                enumSets(),
                publicViews()
        ).as(RadialMenuPayload::new);
    }

    @Provide
    Arbitrary<PassportSnapshotPayload> passportSnapshots() {
        return passportDatas().map(PassportSnapshotPayload::new);
    }

    @Provide
    Arbitrary<PlayerPublicViewPayload> playerPublicViews() {
        return Combinators.combine(
                uuids(),
                Arbitraries.of(FactionId.values()),
                Arbitraries.of(Role.values()),
                Arbitraries.of(PlayerState.values()),
                Arbitraries.of(true, false),
                safeStrings(1, 64).injectNull(0.3)
        ).as(PlayerPublicViewPayload::new);
    }

    @Provide
    Arbitrary<AuthErrorPayload> authErrors() {
        return Combinators.combine(
                safeStrings(1, 128),
                Arbitraries.integers().between(0, 5).flatMap(count ->
                        safeStrings(1, 128).list().ofSize(count))
        ).as(AuthErrorPayload::new);
    }

    @Provide
    Arbitrary<OpenFactionChoicePayload> openFactionChoices() {
        return Arbitraries.of("ZARNAVIA", "CHERNOGRYAD").map(OpenFactionChoicePayload::new);
    }

    @Provide
    Arbitrary<OpenPassportPayload> openPassports() {
        return Arbitraries.of(true, false).map(OpenPassportPayload::new);
    }

    // ==================== HELPER GENERATORS ====================

    private Arbitrary<UUID> uuids() {
        return Arbitraries.create(UUID::randomUUID);
    }

    private Arbitrary<EnumSet<RadialMenuItem>> enumSets() {
        return Arbitraries.of(RadialMenuItem.values())
                .set().ofMinSize(0).ofMaxSize(RadialMenuItem.values().length)
                .map(set -> set.isEmpty() ? EnumSet.noneOf(RadialMenuItem.class) : EnumSet.copyOf(set));
    }

    private Arbitrary<PublicView> publicViews() {
        return Combinators.combine(
                uuids(),
                Arbitraries.of(FactionId.values()),
                Arbitraries.of(Role.values()),
                Arbitraries.of(PlayerState.values()),
                Arbitraries.of(true, false),
                safeStrings(1, 64).injectNull(0.3)
        ).as(PublicView::new);
    }

    private Arbitrary<PassportData> passportDatas() {
        Arbitrary<String> passportIds = Arbitraries.of("ZRN-", "CHN-")
                .flatMap(p -> Arbitraries.integers().between(1, 999999)
                        .map(n -> p + String.format("%06d", n)));

        return Combinators.combine(
                passportIds,
                Arbitraries.of(FactionId.values()),
                safeStrings(1, 32),
                safeStrings(1, 32),
                Arbitraries.of("01.01.1980", "15.06.1995", "28.02.2000", "31.12.1974"),
                Arbitraries.longs(),
                Arbitraries.of(PlayerState.values()),
                Arbitraries.longs().injectNull(0.4)
        ).flatAs((id, faction, rpName, rpSurname, dob, sigSeed, status, acceptedAt) ->
                Combinators.combine(
                        safeStrings(1, 32).injectNull(0.4),
                        Arbitraries.of(true, false)
                ).as((acceptedBy, trophy) ->
                        new PassportData(id, faction, rpName, rpSurname, dob, sigSeed, status, acceptedAt, acceptedBy, trophy))
        );
    }

    /**
     * Generates safe strings that won't exceed the max length limits of writeUtf/readUtf.
     * Uses alphanumeric characters to avoid encoding issues.
     */
    private Arbitrary<String> safeStrings(int minLen, int maxLen) {
        return Arbitraries.strings()
                .withCharRange('a', 'z')
                .withCharRange('A', 'Z')
                .withCharRange('0', '9')
                .ofMinLength(minLen)
                .ofMaxLength(maxLen);
    }

    // ==================== ROUND-TRIP HELPER ====================

    private <T> void assertRoundTrip(StreamCodec<RegistryFriendlyByteBuf, T> codec, T original) {
        RegistryFriendlyByteBuf buf = new RegistryFriendlyByteBuf(Unpooled.buffer(), RegistryAccess.EMPTY);
        try {
            codec.encode(buf, original);
            T decoded = codec.decode(buf);
            assertThat(decoded).isEqualTo(original);
            // Verify buffer is fully consumed (no leftover bytes)
            assertThat(buf.readableBytes()).isZero();
        } finally {
            buf.release();
        }
    }
}
