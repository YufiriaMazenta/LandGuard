package pers.yufiria.landguard.ui;

import crypticlib.CommonPlayer;
import crypticlib.conversation.Conversation;
import crypticlib.conversation.Prompt;
import crypticlib.lang.entry.StringLangEntry;
import net.md_5.bungee.api.chat.BaseComponent;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import pers.yufiria.landguard.LandGuard;
import pers.yufiria.landguard.util.CommandUtils;
import pers.yufiria.landguard.util.LangUtils;

import java.util.Map;
import java.util.function.Consumer;

/**
 * GUI 关闭后的单行聊天输入：把玩家输入交给回调。
 * 输入 {@code cancel}、超时或空白输入都不回调（即静默取消）。
 * 领地改名/转让与组织创建/改名/邀请共用同一实现，避免每个菜单各写一份 Prompt。
 */
final class ChatPrompt implements Prompt {

    private static final String INPUT_KEY = "input";
    private static final String CANCEL_WORD = "cancel";

    private final CommonPlayer player;
    private final StringLangEntry promptEntry;

    private ChatPrompt(@NotNull CommonPlayer player, @NotNull StringLangEntry promptEntry) {
        this.player = player;
        this.promptEntry = promptEntry;
    }

    /** 关闭当前菜单并向玩家索要一行文本，输入会 trim 后回调。 */
    static void ask(@NotNull Player player, @NotNull StringLangEntry promptEntry, @NotNull Consumer<String> onInput) {
        player.closeInventory();
        new Conversation(LandGuard.instance(), player,
            new ChatPrompt(CommandUtils.commonPlayer(player), promptEntry), CANCEL_WORD, data -> {
            Object input = data.get(INPUT_KEY);
            if (input instanceof String raw && !raw.isBlank()) {
                onInput.accept(raw.trim());
            }
        }).start();
    }

    @Override
    public Prompt acceptInput(Map<Object, Object> conversationData, String input) {
        conversationData.put(INPUT_KEY, input);
        return null;
    }

    @Override
    public BaseComponent promptText(Map<Object, Object> conversationData) {
        return LangUtils.component(player, promptEntry, Map.of());
    }

}