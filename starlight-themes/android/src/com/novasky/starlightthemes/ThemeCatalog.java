package com.novasky.starlightthemes;

/** Single source of truth for Starlight's theme identities and launcher-owned chrome.
 *  Theme IDs are stable: preferences and layout must never be reset by a theme change.
 *  This pure-Java class is host-testable without an Android SDK.
 */
public final class ThemeCatalog {
    private ThemeCatalog() {}
    public static final class ThemeSpec {
        public final String id,name,subtitle,iconPalette;
        public final int accent,tint,iconTop,iconBottom,iconStroke,glyph,woodTop,woodBottom,shelf;
        public final int[] bookColors;
        private ThemeSpec(String id,String name,String subtitle,String iconPalette,int accent,int tint,
                          int iconTop,int iconBottom,int iconStroke,int glyph,int woodTop,int woodBottom,int shelf,int[] books) {
            this.id=id;this.name=name;this.subtitle=subtitle;this.iconPalette=iconPalette;
            this.accent=accent;this.tint=tint;this.iconTop=iconTop;this.iconBottom=iconBottom;
            this.iconStroke=iconStroke;this.glyph=glyph;this.woodTop=woodTop;this.woodBottom=woodBottom;
            this.shelf=shelf;this.bookColors=books.clone();
        }
    }
    public static final ThemeSpec[] ALL = {
        // Original colors and IDs intentionally preserved for existing installations.
        new ThemeSpec("moonflower","Moonflower","Moths, moonlight and a little magic","petal",
            0xffe8c8ed,0xff29213f,0xfff2deed,0xffd5c0df,0xffaa8eba,0xffa96287,0xff5d354c,0xff322339,0xffcb9e9d,
            new int[]{0xff79526a,0xff745773,0xff705666,0xff805768}),
        new ThemeSpec("conservatory","Starlit Conservatory","A quiet garden under the stars","midnight",
            0xffc5dded,0xff1e3446,0xff382453,0xff241a40,0xff8b78a5,0xffe8c8ed,0xff393047,0xff211c32,0xffbba0aa,
            new int[]{0xff49415f,0xff514362,0xff3d4b62,0xff58455b}),
        // Three additional coherent families: backgrounds, icons, bookcases and launcher chrome.
        new ThemeSpec("lanternwood","Fae Lanternwood","Fireflies, woodland spirits and dusky green lanterns","lanternwood",
            0xffd1e9ab,0xff1c3b36,0xff1b4b3e,0xff10372d,0xffe6c97c,0xfff7edb2,0xff3c4b36,0xff1b342d,0xff90ab7d,
            new int[]{0xff4b694e,0xff3c5941,0xff607354,0xff506a48}),
        new ThemeSpec("astralclock","Astral Clockwork","Celestial gears, midnight ink and silver stardust","astralclock",
            0xffe3d8b0,0xff1b2548,0xff1a3565,0xff111f43,0xffd7b775,0xffeee5c4,0xff2a3656,0xff141e3c,0xffa7a5c9,
            new int[]{0xff2b4a72,0xff394a68,0xff3d3e66,0xff2d4260}),
        new ThemeSpec("velvetmasque","Velvet Masquerade","Rose velvet, gothic windows and candlelit fairy tales","velvetmasque",
            0xffe8c5d6,0xff342437,0xff74334f,0xff461c42,0xffd8a97e,0xffffe1ed,0xff54394d,0xff2b1b33,0xffb78c9e,
            new int[]{0xff814765,0xff6e3656,0xff78435b,0xff63435c})
    };
    public static ThemeSpec find(String id) {
        if (id != null) for (ThemeSpec t: ALL) if (t.id.equals(id)) return t;
        return ALL[0];
    }
}