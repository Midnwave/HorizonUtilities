package com.blockforge.horizonutilities.story;

import com.blockforge.horizonutilities.HorizonUtilitiesPlugin;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BookMeta;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class StoryBookManager {

    private final HorizonUtilitiesPlugin plugin;
    private final StoryConfig config;
    private static final MiniMessage MINI = MiniMessage.miniMessage();

    public StoryBookManager(HorizonUtilitiesPlugin plugin, StoryConfig config) {
        this.plugin = plugin;
        this.config = config;
    }

    /**
     * Creates a written book from the story config by stage and book ID.
     */
    public ItemStack createBook(String stageKey, String bookId) {
        StoryConfig.BookData bookData = config.getBook(stageKey, bookId);
        if (bookData == null) return null;
        return buildWrittenBook(bookData.title(), bookData.author(), bookData.pages());
    }

    /**
     * Creates the first-join lore book.
     */
    public ItemStack createFirstJoinBook() {
        return buildWrittenBook(
            config.getFirstJoinBookTitle(),
            config.getFirstJoinBookAuthor(),
            config.getFirstJoinBookPages()
        );
    }

    /**
     * Gives a book to a player by stage key and book ID.
     * Returns true if the book was found and given.
     */
    public boolean giveBook(Player player, String stageKey, String bookId) {
        ItemStack book = createBook(stageKey, bookId);
        if (book == null) return false;
        player.getInventory().addItem(book);
        return true;
    }

    /**
     * Gives a book by just the book ID, searching all stages.
     */
    public boolean giveBookById(Player player, String bookId) {
        // Search all stages for this book ID
        for (int s = 1; s <= 7; s++) {
            String stageKey = "stage-" + s;
            Map<String, StoryConfig.BookData> stageBooks = config.getBooksForStage(stageKey);
            if (stageBooks.containsKey(bookId)) {
                return giveBook(player, stageKey, bookId);
            }
        }
        return false;
    }

    /**
     * Lists all available book IDs across all stages.
     */
    public List<String> getAllBookIds() {
        List<String> ids = new ArrayList<>();
        for (int s = 1; s <= 7; s++) {
            ids.addAll(config.getBooksForStage("stage-" + s).keySet());
        }
        return ids;
    }

    /**
     * Builds a WRITTEN_BOOK ItemStack with MiniMessage-parsed pages.
     */
    private ItemStack buildWrittenBook(String title, String author, List<String> pages) {
        ItemStack book = new ItemStack(Material.WRITTEN_BOOK);
        BookMeta meta = (BookMeta) book.getItemMeta();

        meta.setTitle(title);
        meta.setAuthor(author);
        meta.setGeneration(BookMeta.Generation.ORIGINAL);

        for (String page : pages) {
            Component pageComponent = MINI.deserialize(page);
            meta.addPages(pageComponent);
        }

        book.setItemMeta(meta);
        return book;
    }
}
