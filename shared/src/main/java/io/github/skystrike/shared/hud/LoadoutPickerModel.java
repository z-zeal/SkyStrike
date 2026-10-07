package io.github.skystrike.shared.hud;

import io.github.skystrike.shared.gadget.GadgetDefinition;
import io.github.skystrike.shared.gadget.GadgetId;
import io.github.skystrike.shared.gadget.GadgetRegistry;
import io.github.skystrike.shared.model.LoadoutOptions;
import io.github.skystrike.shared.model.PlayerLoadout;
import io.github.skystrike.shared.model.WeaponItem;
import io.github.skystrike.shared.net.c2s.PacketLoadoutUpdate;
import io.github.skystrike.shared.utility.UtilityDefinition;
import io.github.skystrike.shared.utility.UtilityId;
import io.github.skystrike.shared.utility.UtilityRegistry;
import io.github.skystrike.shared.weapons.MeleeDefinition;
import io.github.skystrike.shared.weapons.MeleeId;
import io.github.skystrike.shared.weapons.MeleeRegistry;
import io.github.skystrike.shared.weapons.WeaponDefinition;
import io.github.skystrike.shared.weapons.WeaponId;
import io.github.skystrike.shared.weapons.WeaponRegistry;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * The loadout picker's state and catalogue (playable build plan M4 §5), with no drawing in it.
 *
 * <p>The picker half of the HUD is "a grid of weapons from the registries, writing a
 * {@link PacketLoadoutUpdate}". Everything about that except the pixels is decidable without a
 * screen — which options a slot may show ({@link LoadoutOptions}, the same table the server
 * validates against), where the cursor is, what the current composition is, and what the packet
 * should therefore say — so all of it lives here, in {@code shared}, where CI can prove it.
 *
 * <p>Two rules the packet obeys:
 * <ul>
 *   <li><b>Only changes travel.</b> A field the player did not move stays
 *       {@link PacketLoadoutUpdate#KEEP_CURRENT}, so opening the picker and closing it again
 *       sends nothing and cannot disturb a composition.</li>
 *   <li><b>The baseline is the live loadout.</b> {@link #syncFrom(PlayerLoadout)} re-reads it,
 *       which is how the picker shows what you are actually carrying rather than what you asked
 *       for last time.</li>
 * </ul>
 *
 * <p>The server applies a composition <b>at the next respawn</b> ({@code LoadoutSystem}), which
 * {@link #APPLIES_AT_RESPAWN} states on screen — a picker that silently changes nothing until
 * you die reads as broken otherwise.
 */
public final class LoadoutPickerModel {

    /**
     * The on-screen promise the server actually keeps. Plain ASCII punctuation on purpose: the
     * HUD font is baked over Latin-1 plus two symbols ({@code FontManager}), so an em dash here
     * would draw as a missing glyph in the one sentence the picker most needs read.
     */
    public static final String APPLIES_AT_RESPAWN =
        "Changes apply at your next respawn - the server rebuilds the loadout then.";

    /** The seven things a player picks. Order is the order the columns appear in. */
    public enum Category {
        PRIMARY("Primary", "1"),
        SIDEARM("Sidearm", "2"),
        MELEE("Melee", "3"),
        UTILITY_A("Throwable A", "4"),
        UTILITY_B("Throwable B", "5"),
        GADGET_Q("Gadget Q", "Q"),
        GADGET_E("Gadget E", "E");

        private final String title;
        private final String slotLabel;

        Category(String title, String slotLabel) {
            this.title = title;
            this.slotLabel = slotLabel;
        }

        public String title() {
            return title;
        }

        /** The key that selects this slot in the match, for the column header. */
        public String slotLabel() {
            return slotLabel;
        }
    }

    /**
     * One row of the grid.
     *
     * @param ordinal the wire ordinal this row writes into the packet
     * @param label   the display name
     * @param detail  the one-line summary under the name
     */
    public record Option(int ordinal, String label, String detail) {
    }

    private final Map<Category, List<Option>> options = new EnumMap<>(Category.class);
    private final Map<Category, Integer> cursor = new EnumMap<>(Category.class);
    private final Map<Category, Integer> baseline = new EnumMap<>(Category.class);

    private Category category = Category.PRIMARY;

    public LoadoutPickerModel() {
        options.put(Category.PRIMARY, gunOptions(LoadoutOptions.primaries()));
        options.put(Category.SIDEARM, gunOptions(LoadoutOptions.sidearms()));
        options.put(Category.MELEE, meleeOptions());
        List<Option> utilities = utilityOptions();
        options.put(Category.UTILITY_A, utilities);
        options.put(Category.UTILITY_B, utilities);
        List<Option> gadgets = gadgetOptions();
        options.put(Category.GADGET_Q, gadgets);
        options.put(Category.GADGET_E, gadgets);
        for (Category c : Category.values()) {
            cursor.put(c, 0);
            baseline.put(c, options.get(c).isEmpty() ? -1 : options.get(c).get(0).ordinal());
        }
    }

    // --- Catalogue ------------------------------------------------------------------------------

    public List<Option> options(Category category) {
        return options.getOrDefault(category, List.of());
    }

    public List<Option> currentOptions() {
        return options(category);
    }

    public Category category() {
        return category;
    }

    public void setCategory(Category category) {
        if (category != null) {
            this.category = category;
        }
    }

    /** Moves to another column, wrapping in both directions. */
    public void cycleCategory(int step) {
        Category[] all = Category.values();
        int index = ((category.ordinal() + step) % all.length + all.length) % all.length;
        category = all[index];
    }

    // --- Cursor ---------------------------------------------------------------------------------

    /** Index of the highlighted row in {@code category}. */
    public int cursor(Category category) {
        return cursor.getOrDefault(category, 0);
    }

    public int cursor() {
        return cursor(category);
    }

    /** Moves the current column's cursor, clamped to the list (no wrap: a long gun list ends). */
    public void moveCursor(int delta) {
        setCursor(cursor() + delta);
    }

    /** Puts the current column's cursor at {@code index}, clamped. */
    public void setCursor(int index) {
        int size = currentOptions().size();
        if (size == 0) {
            cursor.put(category, 0);
            return;
        }
        cursor.put(category, Math.min(size - 1, Math.max(0, index)));
    }

    /** The highlighted option of the current column, or {@code null} for an empty column. */
    public Option selected() {
        List<Option> list = currentOptions();
        return list.isEmpty() ? null : list.get(cursor());
    }

    /** The highlighted option of any column. */
    public Option selected(Category category) {
        List<Option> list = options(category);
        if (list.isEmpty()) {
            return null;
        }
        return list.get(Math.min(list.size() - 1, Math.max(0, cursor(category))));
    }

    /** The ordinal the given column currently points at, or −1 for an empty column. */
    public int selectedOrdinal(Category category) {
        Option option = selected(category);
        return option == null ? -1 : option.ordinal();
    }

    // --- Baseline and packet ---------------------------------------------------------------------

    /**
     * Re-reads the live composition: every cursor jumps to what is actually carried, and that
     * becomes the baseline against which "changed" is measured. A loadout whose weapon is not in
     * a column (it cannot be, today — every registry row is offered) leaves that column alone.
     */
    public void syncFrom(PlayerLoadout loadout) {
        if (loadout == null) {
            return;
        }
        syncOne(Category.PRIMARY, loadout.primary);
        syncOne(Category.SIDEARM, loadout.handgun);
        point(Category.MELEE, loadout.melee);
        point(Category.UTILITY_A, utilityOrdinal(loadout, PlayerLoadout.SLOT_UTILITY_A));
        point(Category.UTILITY_B, utilityOrdinal(loadout, PlayerLoadout.SLOT_UTILITY_B));
        point(Category.GADGET_Q, loadout.gadgetSlot(0).gadgetId().ordinal());
        point(Category.GADGET_E, loadout.gadgetSlot(1).gadgetId().ordinal());
        for (Category c : Category.values()) {
            baseline.put(c, selectedOrdinal(c));
        }
    }

    /** True when {@code category}'s cursor has moved off what the player is carrying. */
    public boolean isChanged(Category category) {
        int base = baseline.getOrDefault(category, -1);
        int now = selectedOrdinal(category);
        return now >= 0 && now != base;
    }

    /** True when anything at all would be sent. */
    public boolean isDirty() {
        for (Category c : Category.values()) {
            if (isChanged(c)) {
                return true;
            }
        }
        return false;
    }

    /**
     * The request to send: changed fields only, {@link PacketLoadoutUpdate#KEEP_CURRENT}
     * everywhere else. An unchanged picker produces a packet whose {@code isEmpty()} is true,
     * which the server's handler drops — so "open, look, close" is free.
     */
    public PacketLoadoutUpdate toPacket() {
        return new PacketLoadoutUpdate(
            field(Category.PRIMARY),
            field(Category.SIDEARM),
            field(Category.MELEE),
            field(Category.UTILITY_A),
            field(Category.UTILITY_B),
            field(Category.GADGET_Q),
            field(Category.GADGET_E));
    }

    /** Accepts the current selection as the new baseline, after a request has been sent. */
    public void commit() {
        for (Category c : Category.values()) {
            baseline.put(c, selectedOrdinal(c));
        }
    }

    private int field(Category category) {
        return isChanged(category) ? selectedOrdinal(category) : PacketLoadoutUpdate.KEEP_CURRENT;
    }

    private void syncOne(Category category, WeaponItem item) {
        if (item != null && item.hasWeapon()) {
            point(category, item.weapon);
        }
    }

    private static int utilityOrdinal(PlayerLoadout loadout, int slot) {
        UtilityId id = loadout.utilityIdForSlot(slot);
        return id == null ? -1 : id.ordinal();
    }

    /** Points a column's cursor at the row carrying {@code ordinal}, if it has one. */
    private void point(Category category, int ordinal) {
        List<Option> list = options(category);
        for (int i = 0; i < list.size(); i++) {
            if (list.get(i).ordinal() == ordinal) {
                cursor.put(category, i);
                return;
            }
        }
    }

    // --- Option construction ----------------------------------------------------------------------

    private static List<Option> gunOptions(List<WeaponId> ids) {
        List<Option> out = new ArrayList<>(ids.size());
        for (WeaponId id : ids) {
            WeaponDefinition definition = WeaponRegistry.of(id);
            out.add(new Option(id.ordinal(), id.displayName(), String.format(
                Locale.ROOT,
                "%s  dmg %.0f  mag %d/%d",
                titleCase(definition.ballistics().weaponClass().name()),
                definition.damage(),
                definition.magazineSize(),
                definition.reserveAmmo())));
        }
        return List.copyOf(out);
    }

    private static List<Option> meleeOptions() {
        List<Option> out = new ArrayList<>();
        for (MeleeId id : LoadoutOptions.meleeWeapons()) {
            MeleeDefinition definition = MeleeRegistry.of(id);
            out.add(new Option(id.ordinal(), id.displayName(), String.format(
                Locale.ROOT,
                "dmg %.0f  reach %.0f  %.1f/s",
                definition.damage(),
                definition.range(),
                definition.swingsPerSecond())));
        }
        return List.copyOf(out);
    }

    private static List<Option> utilityOptions() {
        List<Option> out = new ArrayList<>();
        for (UtilityId id : LoadoutOptions.utilities()) {
            UtilityDefinition definition = UtilityRegistry.of(id);
            out.add(new Option(id.ordinal(), id.displayName(), String.format(
                Locale.ROOT,
                "x%d  %s  radius %.0f",
                definition.carriedCount(),
                titleCase(definition.effect().name()),
                definition.radius())));
        }
        return List.copyOf(out);
    }

    private static List<Option> gadgetOptions() {
        List<Option> out = new ArrayList<>();
        for (GadgetId id : LoadoutOptions.gadgets()) {
            if (!id.isReal()) {
                out.add(new Option(id.ordinal(), "Empty", "carry nothing in this slot"));
                continue;
            }
            GadgetDefinition definition = GadgetRegistry.of(id);
            String durability = definition.hasDurabilityPool()
                ? String.format(Locale.ROOT, "  %.0f hp", definition.maxDurability())
                : "";
            out.add(new Option(id.ordinal(), id.displayName(),
                titleCase(definition.behavior().name()) + durability));
        }
        return List.copyOf(out);
    }

    /** {@code ASSAULT_RIFLE} → {@code Assault Rifle}: enum names are not UI copy. */
    static String titleCase(String shoutySnakeCase) {
        if (shoutySnakeCase == null || shoutySnakeCase.isEmpty()) {
            return "";
        }
        String[] words = shoutySnakeCase.split("_");
        StringBuilder out = new StringBuilder(shoutySnakeCase.length());
        for (String word : words) {
            if (word.isEmpty()) {
                continue;
            }
            if (out.length() > 0) {
                out.append(' ');
            }
            out.append(Character.toUpperCase(word.charAt(0)))
                .append(word.substring(1).toLowerCase(Locale.ROOT));
        }
        return out.toString();
    }
}
