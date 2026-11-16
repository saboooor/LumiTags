package org.luminaramc.lumitags.command;

import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;
import org.luminaramc.lumitags.LumiTags;
import org.luminaramc.lumitags.manager.LuckPermsManager;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class LumiTagCommandTest {
    @Test void presetAccessIsIndependentOfCustomTextAccess() {
        for (boolean presetAccess : new boolean[]{false, true}) {
            LumiTags plugin = mock(LumiTags.class);
            Player player = mock(Player.class);
            LuckPermsManager manager = mock(LuckPermsManager.class);
            var config = new YamlConfiguration();
            config.set("presets.coffee.prefix", "Coffee");
            config.set("presets.wave.suffix", "Wave");
            when(plugin.getConfig()).thenReturn(config);
            when(plugin.getLuckPermsManager()).thenReturn(manager);
            when(player.hasPermission("lumitags.use")).thenReturn(!presetAccess);
            when(player.hasPermission("lumitags.presets")).thenReturn(presetAccess);
            var command = new LumiTagCommand(plugin);
            for (String input : new String[]{"CoFfEe", "wave", "custom"}) {
                command.onCommand(player, null, "lumitags", new String[]{"set", input});
                if (input.equals("custom") != presetAccess) {
                    verify(manager).setLumiTag(eq(player.getUniqueId()), eq(input), any());
                } else {
                    verifyNoInteractions(manager);
                }
                clearInvocations(manager);
            }
            assertEquals(presetAccess ? java.util.List.of("coffee", "wave") : java.util.List.of(),
                    command.onTabComplete(player, null, "lumitags", new String[]{"set", ""}));
        }
    }
}
