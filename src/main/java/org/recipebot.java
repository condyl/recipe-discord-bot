package org.example;

import com.github.ygimenez.exception.InvalidHandlerException;
import com.github.ygimenez.method.Pages;
import com.github.ygimenez.model.PaginatorBuilder;
import io.github.cdimascio.dotenv.Dotenv;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.OnlineStatus;
import net.dv8tion.jda.api.entities.Activity;
import net.dv8tion.jda.api.exceptions.InvalidTokenException;
import net.dv8tion.jda.api.requests.GatewayIntent;
import net.dv8tion.jda.api.sharding.DefaultShardManagerBuilder;
import net.dv8tion.jda.api.sharding.ShardManager;
import net.dv8tion.jda.api.utils.ChunkingFilter;
import net.dv8tion.jda.api.utils.MemberCachePolicy;
import org.example.commands.CommandsManager;
import org.example.listeners.EventListener;

public class recipebot {

    private final Dotenv config;
    private final ShardManager shardManager;


    public recipebot() throws InvalidTokenException, InvalidHandlerException {

        // hidden token stuff hehe
        config = Dotenv.configure().load();
        String token = config.get("TOKEN");

        // shard manager builder (creates instance of the bot ig idk lol)
        DefaultShardManagerBuilder builder = DefaultShardManagerBuilder.createDefault(token);
        builder.setStatus(OnlineStatus.ONLINE);
        builder.setActivity(Activity.watching("RecipeBot"));
        builder.enableIntents(GatewayIntent.GUILD_MEMBERS, GatewayIntent.MESSAGE_CONTENT, GatewayIntent.GUILD_PRESENCES); // *****

        builder.setMemberCachePolicy(MemberCachePolicy.ALL);
        builder.setChunkingFilter(ChunkingFilter.ALL);

        shardManager = builder.build();

        Pages.activate(PaginatorBuilder.createSimplePaginator(shardManager));

        // listeners
        shardManager.addEventListener(new EventListener());
        shardManager.addEventListener(new CommandsManager());
    }

    public ShardManager getShardManager(){
        return shardManager;
    }

    public Dotenv getConfig(){
        return config;
    }

    public static void main(String[] args) {
        try {
            recipebot bot = new recipebot();

        }
        catch (InvalidTokenException e){
            System.out.println("ERROR: invalid token.");
        } catch (InvalidHandlerException e) {
            throw new RuntimeException(e);
        }
    }

}
