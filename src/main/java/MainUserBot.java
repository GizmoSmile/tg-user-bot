

import it.tdlight.client.*;
import it.tdlight.client.AuthenticationData;
import it.tdlight.client.CommandHandler;
import it.tdlight.client.SimpleTelegramClient;
import it.tdlight.client.TDLibSettings;
import it.tdlight.common.Init;
import it.tdlight.common.utils.CantLoadLibrary;
import it.tdlight.jni.TdApi;

import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Objects;
import java.util.Properties;
import java.util.logging.FileHandler;
import java.util.logging.Logger;
import java.util.logging.SimpleFormatter;
import java.util.regex.Pattern;

/**
 * Example class for TDLight Java
 * <p>
 * The documentation of the TDLight functions can be found here: https://tdlight-team.github.io/tdlight-docs
 */
public final class MainUserBot {

    private static SimpleTelegramClient client;

    private static final Logger LOG = Logger.getLogger(MainUserBot.class.getName());

    public static void main(String[] args) throws CantLoadLibrary, InterruptedException {
        // This block configures logger with handler and formatter
        try {
            FileHandler fh = new FileHandler();
            LOG.addHandler(fh);
            SimpleFormatter formatter = new SimpleFormatter();
            fh.setFormatter(formatter);
        } catch (SecurityException | IOException e) {
            e.printStackTrace();
        }
        // Initialize TDLight native libraries
        Init.start();

        // Obtain the API token
        APIToken apiToken = APIToken.example();

        // Configure the client
        TDLibSettings settings = TDLibSettings.create(apiToken);

        // Configure the session directory
        Path sessionPath = Paths.get("example-tdlight-session");
        settings.setDatabaseDirectoryPath(sessionPath.resolve("data"));
        settings.setDownloadedFilesDirectoryPath(sessionPath.resolve("downloads"));

        // Create a client
        client = new SimpleTelegramClient(settings);

        // Configure the authentication info
        ConsoleInteractiveAuthenticationData authenticationData = AuthenticationData.consoleLogin();

        // Add an example update handler that prints when the bot is started
        client.addUpdateHandler(TdApi.UpdateAuthorizationState.class, MainUserBot::onUpdateAuthorizationState);

        // Add an example update handler that prints every received message
        client.addUpdateHandler(TdApi.UpdateNewMessage.class, MainUserBot::onUpdateNewMessage);

        // Add an example command handler that stops the bot
        client.addCommandHandler("stop", new StopCommandHandler());

        // Start the client
        client.start(authenticationData);

        // Wait for exit
        client.waitForExit();
    }

    //Load properties
    public static Properties getProps() {
        String rootPath = Objects.requireNonNull(Thread.currentThread().getContextClassLoader().
                getResource("")).getPath();
        String startConfigPath = rootPath + "config.xml";
        Properties startConfigProps = new Properties();
        try {
            startConfigProps.loadFromXML(new FileInputStream(startConfigPath));
        } catch (IOException e) {
            e.printStackTrace();
        }
        return startConfigProps;
    }

    /**
     * Print new messages received via updateNewMessage
     */
    private static void onUpdateNewMessage(TdApi.UpdateNewMessage update) {
        //Get keyword from properties and a search pattern
        final String keyword = getProps().getProperty("keyword");
        final Pattern pattern = Pattern.compile(".*" + keyword + ".*", Pattern.CASE_INSENSITIVE |
                Pattern.UNICODE_CASE);

        //Get chat ID from properties
        final long ourChatId = Long.parseLong(getProps().getProperty("chatID"));

        // Get the message content
        TdApi.MessageContent messageContent = update.message.content;

        // Get the message text
        String text;
        if (messageContent instanceof TdApi.MessageText) {
            // Get the text of the text message
            TdApi.MessageText messageText = (TdApi.MessageText) messageContent;
            text = messageText.text.text;
        } else {
            // We handle only text messages, the other messages will be printed as their type
            text = String.format("(%s)", messageContent.getClass().getSimpleName());
        }

        // Get the chat title
        if (update.message.chatId != ourChatId) {
            client.send(new TdApi.GetChat(update.message.chatId), chatIdResult -> {
                // Get the chat response
                TdApi.Chat chat = chatIdResult.get();
                // Get the chat name
                String chatName = chat.title;
                // Check the message for pattern
                if (pattern.matcher(text).matches()) {
                    // Print the message
                    System.out.printf("Found a new message from chat %s: %s%n", chatName, text);
                    LOG.info("In chat: " + chatName + " [A MSG FOUND: ]" + text);

                    long[] messageId = new long[]{
                            update.message.id
                    };

                    client.send(new TdApi.ForwardMessages(ourChatId, update.message.chatId, messageId, null,
                            false, false, false), null);

                    LOG.info("Message: " + text + " [BEEN FORWARDED]" + "\n");

                }

            });
        }
    }

    /**
     * Close the bot if the /stop command is sent by the administrator
     */
    private static class StopCommandHandler implements CommandHandler {

        @Override
        public void onCommand(TdApi.Chat chat, TdApi.MessageSender commandSender, String arguments) {
            // Check if the sender is the admin
            if (isAdmin(commandSender)) {
                // Stop the client
                System.out.println("Received stop command. closing...");
                client.sendClose();
            }
        }
    }

    /**
     * Print the bot status
     */
    private static void onUpdateAuthorizationState(TdApi.UpdateAuthorizationState update) {
        TdApi.AuthorizationState authorizationState = update.authorizationState;
        if (authorizationState instanceof TdApi.AuthorizationStateReady) {
            System.out.println("Logged in");
        } else if (authorizationState instanceof TdApi.AuthorizationStateClosing) {
            System.out.println("Closing...");
        } else if (authorizationState instanceof TdApi.AuthorizationStateClosed) {
            System.out.println("Closed");
        } else if (authorizationState instanceof TdApi.AuthorizationStateLoggingOut) {
            System.out.println("Logging out...");
        }
    }

    /**
     * Check if the command sender is admin
     */
    private static boolean isAdmin(TdApi.MessageSender sender) {
        //Get user ID from properties
        long myUserId = Long.parseLong(getProps().getProperty("userID"));
        TdApi.MessageSender ADMIN_ID = new TdApi.MessageSenderUser(myUserId);
        return sender.equals(ADMIN_ID);
    }
}
//Test for commit