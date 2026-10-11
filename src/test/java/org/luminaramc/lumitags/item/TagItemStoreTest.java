package org.luminaramc.lumitags.item;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.UnsafeValues;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemFactory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.MockedStatic;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class TagItemStoreTest {
    @TempDir Path directory;
    final UUID uuid = UUID.randomUUID();
    MockedStatic<Bukkit> bukkit;
    File file;

    @BeforeEach void setup() {
        bukkit = mockStatic(Bukkit.class);
        UnsafeValues unsafe = mock(UnsafeValues.class);
        ItemFactory factory = mock(ItemFactory.class);
        org.bukkit.Server server = mock(org.bukkit.Server.class);
        when(server.getItemFactory()).thenReturn(factory);
        when(factory.ensureServerConversions(any(ItemStack.class))).thenAnswer(call -> call.getArgument(0));
        bukkit.when(Bukkit::getServer).thenReturn(server);
        bukkit.when(Bukkit::getUnsafe).thenReturn(unsafe);
        bukkit.when(Bukkit::getItemFactory).thenReturn(factory);
        when(unsafe.getDataVersion()).thenReturn(3700);
        when(unsafe.getMaterial(anyString(), anyInt())).thenAnswer(call -> Material.valueOf(call.getArgument(0)));
        when(factory.equals(null, null)).thenReturn(true);
        file = directory.resolve("tag-items.yml").toFile();
    }

    @AfterEach void close() { bukkit.close(); }
    ItemStack item(Material material) { return new ItemStack(material); }
    YamlConfiguration saved() { return YamlConfiguration.loadConfiguration(file); }
    String active(String type) { return "players." + uuid + ".active." + type; }
    String refunds() { return "players." + uuid + ".refunds"; }

    @Test void replacementAndUnsetReturnEachAppliedItemOnceAcrossRestart() throws Exception {
        var store = new TagItemStore(file);
        store.replace(uuid, "prefix", item(Material.NAME_TAG));
        assertEquals(0, saved().getList(refunds()).size());
        store = new TagItemStore(file);
        store.replace(uuid, "prefix", item(Material.PAPER));
        store.replace(uuid, "prefix", null);
        store.replace(uuid, "prefix", null);
        var returns = saved().getList(refunds());
        assertEquals(2, returns.size());
        assertEquals(Material.NAME_TAG, ((ItemStack) returns.get(0)).getType());
        assertEquals(Material.PAPER, ((ItemStack) returns.get(1)).getType());
        assertNull(saved().getItemStack(active("prefix")));
    }

    @Test void commandReplacementReturnsItemButDoesNotCreateAnItem() throws Exception {
        var store = new TagItemStore(file);
        store.replace(uuid, "prefix", item(Material.NAME_TAG));
        store.replace(uuid, "prefix", null);
        store.replace(uuid, "prefix", null);
        assertEquals(1, saved().getList(refunds()).size());
    }

    @Test void prefixAndSuffixOwnershipAreIndependent() throws Exception {
        var store = new TagItemStore(file);
        store.replace(uuid, "prefix", item(Material.NAME_TAG));
        store.replace(uuid, "suffix", item(Material.PAPER));
        store.replace(uuid, "suffix", null);
        assertEquals(Material.NAME_TAG, saved().getItemStack(active("prefix")).getType());
        assertEquals(1, saved().getList(refunds()).size());
    }

    @Test void fullInventoryQueuesReturnUntilClaimAndDoesNotDeliverTwice() throws Exception {
        var store = new TagItemStore(file);
        store.refund(uuid, item(Material.PAPER));
        Player player = mock(Player.class);
        PlayerInventory inventory = mock(PlayerInventory.class);
        when(player.getInventory()).thenReturn(inventory);
        when(inventory.getStorageContents()).thenReturn(new ItemStack[36]);
        when(inventory.addItem(any(ItemStack.class))).thenAnswer(call -> new java.util.HashMap<>(Map.of(0, call.getArgument(0))));
        bukkit.when(() -> Bukkit.getPlayer(uuid)).thenReturn(player);
        store.deliver(uuid);
        assertEquals(1, saved().getList(refunds()).size());
        when(inventory.addItem(any(ItemStack.class))).thenReturn(new java.util.HashMap<>());
        store.deliver(uuid);
        store.deliver(uuid);
        assertEquals(0, saved().getList(refunds()).size());
        verify(inventory, times(2)).addItem(any(ItemStack.class));
    }

    @Test void failedOwnershipSaveRetainsOriginalItemAndDoesNotQueueRefund() throws Exception {
        var store = new TagItemStore(file);
        store.replace(uuid, "prefix", item(Material.NAME_TAG));
        Path temporary = directory.resolve("tag-items.yml.tmp");
        Files.createDirectory(temporary);
        assertThrows(IOException.class, () -> store.replace(uuid, "prefix", item(Material.PAPER)));
        Files.delete(temporary);
        store.replace(uuid, "prefix", null);
        assertEquals(1, saved().getList(refunds()).size());
        assertEquals(Material.NAME_TAG, ((ItemStack) saved().getList(refunds()).get(0)).getType());
    }

    @Test void failedRefundSaveDoesNotLeaveAnExtraRefundInMemory() throws Exception {
        var store = new TagItemStore(file);
        Path temporary = directory.resolve("tag-items.yml.tmp");
        Files.createDirectory(temporary);
        assertThrows(IOException.class, () -> store.refund(uuid, item(Material.PAPER)));
        Files.delete(temporary);
        store.refund(uuid, item(Material.PAPER));
        assertEquals(1, saved().getList(refunds()).size());
    }
}
