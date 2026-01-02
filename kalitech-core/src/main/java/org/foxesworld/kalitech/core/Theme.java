package org.foxesworld.kalitech.core;

import com.formdev.flatlaf.FlatDarkLaf;
import com.formdev.flatlaf.FlatLaf;
import com.formdev.flatlaf.FlatPropertiesLaf;

import java.io.InputStream;

public final class Theme {

    public static void setupTheme(String theme) {
        try {
            InputStream themeStream = Theme.class.getClassLoader().getResourceAsStream(theme);

            if(themeStream == null) {
                throw new RuntimeException("Theme "+theme+" file not found in resources");
            }

            FlatPropertiesLaf laf = new FlatPropertiesLaf("Dark Theme", themeStream);
            FlatLaf.setup(laf);

        } catch(Exception ex) {
            FlatLaf.setup(new FlatDarkLaf());
            ex.printStackTrace();
        }

    }
}
