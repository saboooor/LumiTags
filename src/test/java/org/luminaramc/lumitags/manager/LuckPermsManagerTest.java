package org.luminaramc.lumitags.manager;

import net.luckperms.api.LuckPerms;
import net.luckperms.api.model.user.User;
import net.luckperms.api.model.user.UserManager;
import net.luckperms.api.node.Node;
import net.luckperms.api.node.types.PrefixNode;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitScheduler;
import org.junit.jupiter.api.*;
import org.luminaramc.lumitags.LumiTags;
import org.luminaramc.lumitags.item.TagItemStore;
import org.mockito.MockedStatic;

import java.io.IOException;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class LuckPermsManagerTest {
    final UUID uuid = UUID.randomUUID();
    LumiTags plugin;
    UserManager users;
    User user;
    TagItemStore store;
    LuckPermsManager manager;
    MockedStatic<Bukkit> bukkit;
    MockedStatic<PrefixNode> prefixNodes;
    MockedStatic<net.luckperms.api.node.types.SuffixNode> suffixNodes;

    @BeforeEach void setup() {
        plugin = mock(LumiTags.class);
        when(plugin.getConfig()).thenReturn(new YamlConfiguration());
        when(plugin.getLogger()).thenReturn(java.util.logging.Logger.getAnonymousLogger());
        LuckPerms luckPerms = mock(LuckPerms.class);
        users = mock(UserManager.class);
        user = mock(User.class, RETURNS_DEEP_STUBS);
        store = mock(TagItemStore.class);
        when(plugin.getLuckPerms()).thenReturn(luckPerms);
        when(luckPerms.getUserManager()).thenReturn(users);
        when(plugin.getTagItemStore()).thenReturn(store);
        when(users.loadUser(uuid)).thenReturn(CompletableFuture.completedFuture(user));
        when(user.data().toCollection()).thenReturn(List.of());
        when(users.saveUser(user)).thenReturn(CompletableFuture.completedFuture(null));
        bukkit = mockStatic(Bukkit.class);
        BukkitScheduler scheduler = mock(BukkitScheduler.class);
        bukkit.when(Bukkit::getScheduler).thenReturn(scheduler);
        when(scheduler.runTask(eq(plugin), any(Runnable.class))).thenAnswer(call -> {
            ((Runnable) call.getArgument(1)).run();
            return null;
        });
        prefixNodes = mockStatic(PrefixNode.class);
        var prefixBuilder = mock(PrefixNode.Builder.class);
        when(prefixBuilder.build()).thenReturn(mock(PrefixNode.class));
        prefixNodes.when(() -> PrefixNode.builder(anyString(), anyInt())).thenReturn(prefixBuilder);
        suffixNodes = mockStatic(net.luckperms.api.node.types.SuffixNode.class);
        var suffixBuilder = mock(net.luckperms.api.node.types.SuffixNode.Builder.class);
        when(suffixBuilder.build()).thenReturn(mock(net.luckperms.api.node.types.SuffixNode.class));
        suffixNodes.when(() -> net.luckperms.api.node.types.SuffixNode.builder(anyString(), anyInt())).thenReturn(suffixBuilder);
        manager = new LuckPermsManager(plugin);
    }

    @AfterEach void close() { suffixNodes.close(); prefixNodes.close(); bukkit.close(); }

    PrefixNode oldPrefix() {
        PrefixNode old = mock(PrefixNode.class);
        when(old.getPriority()).thenReturn(500);
        when(old.getType()).thenReturn(net.luckperms.api.node.NodeType.PREFIX);
        return old;
    }

    @Test void commandsCannotReplaceItemWhileItsSaveIsPending() throws Exception {
        CompletableFuture<Void> save = new CompletableFuture<>();
        when(users.saveUser(user)).thenReturn(save);
        ItemStack item = new ItemStack(Material.NAME_TAG, 3);
        var applying = manager.applyItem(uuid, false, "coffee", item);
        assertFalse(applying.isDone());
        assertThrows(java.util.concurrent.CompletionException.class,
                () -> manager.setFormattedLumiTag(uuid, "custom").join());
        verifyNoInteractions(store);
        save.complete(null);
        applying.join();
        var returned = org.mockito.ArgumentCaptor.forClass(ItemStack.class);
        verify(store).replace(eq(uuid), eq("prefix"), returned.capture());
        assertEquals(1, returned.getValue().getAmount());
        assertEquals(3, item.getAmount());
        manager.setFormattedLumiTag(uuid, "custom").join();
        verify(store).replace(uuid, "prefix", null);
    }

    @Test void failedLuckPermsSaveDoesNotChangeOwnershipAndReleasesLock() {
        Node old = oldPrefix();
        when(user.data().toCollection()).thenReturn(List.of(old));
        when(users.saveUser(user)).thenReturn(CompletableFuture.failedFuture(new IOException("save failed")),
                CompletableFuture.completedFuture(null));
        assertThrows(java.util.concurrent.CompletionException.class,
                () -> manager.applyItem(uuid, false, "coffee", new ItemStack(Material.NAME_TAG)).join());
        verifyNoInteractions(store);
        verify(user.data()).add(old);
        manager.setFormattedSuffix(uuid, "wave").join();
        assertDoesNotThrow(() -> verify(store).replace(uuid, "suffix", null));
    }

    @Test void failedOwnershipSaveRollsBackLuckPerms() throws Exception {
        Node old = oldPrefix();
        when(user.data().toCollection()).thenReturn(List.of(old));
        doThrow(new IOException("disk full")).when(store).replace(eq(uuid), eq("prefix"), any(ItemStack.class));
        assertThrows(java.util.concurrent.CompletionException.class,
                () -> manager.applyItem(uuid, false, "coffee", new ItemStack(Material.NAME_TAG)).join());
        verify(user.data()).add(old);
        verify(users, times(2)).saveUser(user);
    }
}
