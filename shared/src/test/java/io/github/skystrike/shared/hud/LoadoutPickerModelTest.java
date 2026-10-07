package io.github.skystrike.shared.hud;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.skystrike.shared.gadget.GadgetId;
import io.github.skystrike.shared.model.LoadoutOptions;
import io.github.skystrike.shared.model.PlayerLoadout;
import io.github.skystrike.shared.net.c2s.PacketLoadoutUpdate;
import io.github.skystrike.shared.utility.UtilityId;
import io.github.skystrike.shared.weapons.MeleeId;
import io.github.skystrike.shared.weapons.WeaponId;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The picker's whole behaviour except the pixels: what it may offer, where it starts, and what
 * it sends.
 */
class LoadoutPickerModelTest {

    private LoadoutPickerModel model;
    private PlayerLoadout loadout;

    @BeforeEach
    void setUp() {
        model = new LoadoutPickerModel();
        loadout = new PlayerLoadout();
        model.syncFrom(loadout);
    }

    private static int indexOfOrdinal(List<LoadoutPickerModel.Option> options, int ordinal) {
        for (int i = 0; i < options.size(); i++) {
            if (options.get(i).ordinal() == ordinal) {
                return i;
            }
        }
        return -1;
    }

    private void point(LoadoutPickerModel.Category category, int ordinal) {
        model.setCategory(category);
        int index = indexOfOrdinal(model.currentOptions(), ordinal);
        assertTrue(index >= 0, "the picker must offer ordinal " + ordinal + " in " + category);
        model.setCursor(index);
    }

    @Test
    @DisplayName("each column offers exactly what the server would accept for that slot")
    void columnsMirrorTheServerRules() {
        assertEquals(LoadoutOptions.primaries().size(),
            model.options(LoadoutPickerModel.Category.PRIMARY).size());
        assertEquals(LoadoutOptions.sidearms().size(),
            model.options(LoadoutPickerModel.Category.SIDEARM).size());
        assertEquals(LoadoutOptions.meleeWeapons().size(),
            model.options(LoadoutPickerModel.Category.MELEE).size());
        assertEquals(LoadoutOptions.utilities().size(),
            model.options(LoadoutPickerModel.Category.UTILITY_A).size());
        assertEquals(LoadoutOptions.gadgets().size(),
            model.options(LoadoutPickerModel.Category.GADGET_Q).size());

        for (LoadoutPickerModel.Option option : model.options(LoadoutPickerModel.Category.SIDEARM)) {
            assertTrue(LoadoutOptions.isLegalHandgun(option.ordinal()),
                option.label() + " is offered for slot 2 but the server would drop it");
        }
        assertEquals("Empty", model.options(LoadoutPickerModel.Category.GADGET_Q).get(0).label(),
            "carrying nothing is an explicit first choice, not the absence of one");
    }

    @Test
    @DisplayName("option rows read as a player needs them to")
    void optionText() {
        model.setCategory(LoadoutPickerModel.Category.PRIMARY);
        LoadoutPickerModel.Option carbine =
            model.options(LoadoutPickerModel.Category.PRIMARY).get(WeaponId.IRON_CARBINE.ordinal());
        assertEquals("Iron Carbine", carbine.label());
        assertEquals("Assault Rifle  dmg 32  mag 30/120", carbine.detail());

        LoadoutPickerModel.Option frag =
            model.options(LoadoutPickerModel.Category.UTILITY_A).get(UtilityId.FRAG.ordinal());
        assertEquals("Frag Grenade", frag.label());
        assertEquals("x2  Blast  radius 350", frag.detail());

        LoadoutPickerModel.Option shield =
            model.options(LoadoutPickerModel.Category.GADGET_Q).get(GadgetId.SHIELD.ordinal());
        assertEquals("Ballistic Shield", shield.label());
        assertEquals("Hybrid  150 hp", shield.detail());

        assertEquals("carry nothing in this slot",
            model.options(LoadoutPickerModel.Category.GADGET_E).get(GadgetId.NONE.ordinal()).detail());
        assertEquals("Assault Rifle", LoadoutPickerModel.titleCase("ASSAULT_RIFLE"));
        assertEquals("", LoadoutPickerModel.titleCase(null));
    }

    @Test
    @DisplayName("syncing points every column at what is actually carried")
    void syncFromTheLiveLoadout() {
        assertEquals(WeaponId.DEFAULT.ordinal(),
            model.selectedOrdinal(LoadoutPickerModel.Category.PRIMARY));
        assertEquals(WeaponId.DEFAULT_SIDEARM.ordinal(),
            model.selectedOrdinal(LoadoutPickerModel.Category.SIDEARM));
        assertEquals(MeleeId.DEFAULT.ordinal(),
            model.selectedOrdinal(LoadoutPickerModel.Category.MELEE));
        assertEquals(UtilityId.DEFAULT_PRIMARY.ordinal(),
            model.selectedOrdinal(LoadoutPickerModel.Category.UTILITY_A));
        assertEquals(UtilityId.DEFAULT_SECONDARY.ordinal(),
            model.selectedOrdinal(LoadoutPickerModel.Category.UTILITY_B));
        assertEquals(GadgetId.NONE.ordinal(),
            model.selectedOrdinal(LoadoutPickerModel.Category.GADGET_Q));
        assertFalse(model.isDirty(), "opening the picker changes nothing by itself");
        assertTrue(model.toPacket().isEmpty(), "and sends nothing");

        loadout.setComposition(WeaponId.CATHEDRAL, null, MeleeId.WINTER_KATANA);
        loadout.setGadgets(GadgetId.SHIELD, null);
        model.syncFrom(loadout);
        assertEquals(WeaponId.CATHEDRAL.ordinal(),
            model.selectedOrdinal(LoadoutPickerModel.Category.PRIMARY));
        assertEquals(MeleeId.WINTER_KATANA.ordinal(),
            model.selectedOrdinal(LoadoutPickerModel.Category.MELEE));
        assertEquals(GadgetId.SHIELD.ordinal(),
            model.selectedOrdinal(LoadoutPickerModel.Category.GADGET_Q));
        assertFalse(model.isDirty(), "re-syncing re-baselines; it is not an edit");
    }

    @Test
    @DisplayName("only changed fields travel; the rest keep what the player has")
    void packetCarriesOnlyChanges() {
        point(LoadoutPickerModel.Category.PRIMARY, WeaponId.CATHEDRAL.ordinal());
        point(LoadoutPickerModel.Category.UTILITY_B, UtilityId.FLASHBANG.ordinal());
        point(LoadoutPickerModel.Category.GADGET_E, GadgetId.FUEL_TANK.ordinal());

        assertTrue(model.isDirty());
        assertTrue(model.isChanged(LoadoutPickerModel.Category.PRIMARY));
        assertFalse(model.isChanged(LoadoutPickerModel.Category.SIDEARM));

        PacketLoadoutUpdate packet = model.toPacket();
        assertEquals(WeaponId.CATHEDRAL.ordinal(), packet.primary);
        assertEquals(PacketLoadoutUpdate.KEEP_CURRENT, packet.handgun);
        assertEquals(PacketLoadoutUpdate.KEEP_CURRENT, packet.melee);
        assertEquals(PacketLoadoutUpdate.KEEP_CURRENT, packet.utilityA);
        assertEquals(UtilityId.FLASHBANG.ordinal(), packet.utilityB);
        assertEquals(PacketLoadoutUpdate.KEEP_CURRENT, packet.gadgetQ);
        assertEquals(GadgetId.FUEL_TANK.ordinal(), packet.gadgetE);
        assertFalse(packet.isEmpty());

        model.commit();
        assertFalse(model.isDirty(), "a sent request becomes the new baseline");
        assertTrue(model.toPacket().isEmpty());
    }

    @Test
    @DisplayName("the gadget column can request the empty slot, which is not the keep sentinel")
    void emptyGadgetIsARealRequest() {
        point(LoadoutPickerModel.Category.GADGET_Q, GadgetId.SHIELD.ordinal());
        model.commit();
        point(LoadoutPickerModel.Category.GADGET_Q, GadgetId.NONE.ordinal());

        PacketLoadoutUpdate packet = model.toPacket();
        assertEquals(GadgetId.NONE.ordinal(), packet.gadgetQ);
        assertFalse(packet.isEmpty(), "0 means 'carry nothing', −1 means 'leave it alone'");
    }

    @Test
    @DisplayName("the cursor clamps to its column and columns wrap")
    void cursorAndColumns() {
        model.setCategory(LoadoutPickerModel.Category.UTILITY_A);
        model.setCursor(-5);
        assertEquals(0, model.cursor());
        model.setCursor(9999);
        assertEquals(model.currentOptions().size() - 1, model.cursor());
        model.moveCursor(-1);
        assertEquals(model.currentOptions().size() - 2, model.cursor());
        assertNotNull(model.selected());

        model.setCategory(LoadoutPickerModel.Category.GADGET_E);
        model.cycleCategory(1);
        assertEquals(LoadoutPickerModel.Category.PRIMARY, model.category(), "columns wrap forward");
        model.cycleCategory(-1);
        assertEquals(LoadoutPickerModel.Category.GADGET_E, model.category(), "and backward");
    }

    @Test
    @DisplayName("the two throwable columns and the two gadget columns move independently")
    void columnsAreIndependent() {
        point(LoadoutPickerModel.Category.UTILITY_A, UtilityId.MOLOTOV.ordinal());
        assertEquals(UtilityId.MOLOTOV.ordinal(),
            model.selectedOrdinal(LoadoutPickerModel.Category.UTILITY_A));
        assertEquals(UtilityId.DEFAULT_SECONDARY.ordinal(),
            model.selectedOrdinal(LoadoutPickerModel.Category.UTILITY_B),
            "moving throwable A must not drag throwable B with it");
    }

    @Test
    @DisplayName("the picker states the rule the server actually keeps")
    void saysWhenItApplies() {
        assertEquals(
            "Changes apply at your next respawn - the server rebuilds the loadout then.",
            LoadoutPickerModel.APPLIES_AT_RESPAWN);
    }
}
