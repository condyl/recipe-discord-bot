package org.example.commands;

import com.github.ygimenez.method.Pages;
import com.github.ygimenez.model.InteractPage;
import com.github.ygimenez.model.Page;
import io.github.cdimascio.dotenv.Dotenv;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.events.guild.GuildReadyEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.session.ReadyEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.CommandData;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import net.dv8tion.jda.internal.entities.ReceivedMessage;
import okhttp3.internal.ws.RealWebSocket;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.json.JSONString;

import java.awt.*;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

public class CommandsManager extends ListenerAdapter {

    private Dotenv config;

    @Override
    public void onSlashCommandInteraction(SlashCommandInteractionEvent event) {
        String command = event.getName();

        config = Dotenv.configure().load();
        String key = config.get("APITOKEN");

        if (command.equalsIgnoreCase("getrecipe")) {
            List<Page> pages = new ArrayList<>();
            OptionMapping queryOption = event.getOption("query");
            assert queryOption != null;
            String query = queryOption.getAsString()
                    .replace(" ", "%20");

            // api stuff
            String link = "https://spoonacular-recipe-food-nutrition-v1.p.rapidapi.com/recipes/complexSearch?instructionsRequired=true&addRecipeInformation=true&number=50&sort=calories"
                    + "&"
                    + "query="
                    + query;

            event.deferReply().queue();

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(link))
                    .header("X-RapidAPI-Key", key)
                    .header("X-RapidAPI-Host", "spoonacular-recipe-food-nutrition-v1.p.rapidapi.com")
                    .method("GET", HttpRequest.BodyPublishers.noBody())
                    .build();

            HttpResponse<String> response;
            try {
                response = HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString());
            } catch (IOException | InterruptedException e) {
                throw new RuntimeException(e);
            }

            //System.out.println(response.body());

            JSONObject jo = new JSONObject(response.body());
            JSONArray ja = jo.getJSONArray("results");
            JSONObject dish = new JSONObject();
            try {
                dish = ja.getJSONObject((int) (Math.random() * ja.length()));
            }
            catch (JSONException e){
                EmbedBuilder errorEmbed = new EmbedBuilder();
                errorEmbed.setTitle("Error: JSON Exception")
                        .setDescription("No recipes for *" + query + "* could be found.  Try again.")
                        .setColor(new Color(255,0,0))
                        .setFooter("Made by @condyl#999")
                        .setTimestamp(new Date().toInstant());
                event.getHook().sendMessage("").setEmbeds(errorEmbed.build()).queue();
            }

            System.out.println(dish);

            // json stuff
            String title = dish.getString("title");
            String summary = dish.getString("summary")
                    .replace("<b>","**")
                    .replace("</b>","**")
                    .replace("<a href=\"", "\n")
                    .replace("</a>","")
                    ;
            int lastBoldIndex = 0;
            for(int i = 0; i < summary.length()-1; i++){
                if(summary.charAt(i) == '*'){
                    lastBoldIndex = i;
                }
            }
            summary = summary.substring(0, lastBoldIndex) + "*.";
            String sourceUrl = dish.getString("sourceUrl");
            String sourceName = dish.getString("sourceName");
            String image = dish.getString("image");
            int readyInMinutes = dish.getInt("readyInMinutes");
            int servings = dish.getInt("servings");
            JSONObject nutrition = dish.getJSONObject("nutrition");
            JSONArray nutrients = nutrition.getJSONArray("nutrients");
            JSONObject nutrients2 = nutrients.getJSONObject(0);
            double calories = nutrients2.getDouble("amount");
            int id = dish.getInt("id");

            // json stuff 2 (instructions) (thank you chatgpt)
            JSONArray instructions = dish.getJSONArray("analyzedInstructions");
            String insString = instructions.toString();
            JSONArray temparr = new JSONArray(insString);
            JSONObject tempobj = temparr.getJSONObject(0);
            JSONArray s = tempobj.getJSONArray("steps");

            // get list of ingredients
            ArrayList<String> ingredientNames = new ArrayList<String>(); // list of ingredients (to go in initial embed)
            for (int j = 0; j < s.length(); j++) {
                JSONObject step = s.getJSONObject(j);
                JSONArray ingredients = step.getJSONArray("ingredients");
                for (int k = 0; k < ingredients.length(); k++) {
                    JSONObject ingredient = ingredients.getJSONObject(k);
                    String name = ingredient.getString("name");
                    // manually replacing some
                    name.replace("vegetable", "mixed vegetables");
                    //name = name.substring(0,1).toUpperCase() + name.substring(1).toLowerCase();
                    if(name.contains("chicken")){
                        name = "chicken";
                    }
                    if(!ingredientNames.contains(name) && !name.equals("spread") && !name.equals("wrap")
                            && !name.equals("marinade") && !name.equals("roll") && !name.equals("dip")){
                        ingredientNames.add("• " + name.substring(0,1).toUpperCase() + name.substring(1).toLowerCase());
                    }
                }
            }
            List<String> uniqueList = ingredientNames.stream().distinct().toList();
            ingredientNames = new ArrayList<>(uniqueList);

            // prepare list of ingredients to go in embed
            int totalIngredients = ingredientNames.size();
            int ingredientsPerString = totalIngredients / 3;
            int remainingIngredients = totalIngredients % 3;
            ArrayList<String> strings = new ArrayList<>();
            int startIndex = 0;
            for (int i = 0; i < 3; i++) {
                int endIndex = startIndex + ingredientsPerString + (i < remainingIngredients ? 1 : 0);
                ArrayList<String> subIngredients = new ArrayList<>(ingredientNames.subList(startIndex, endIndex));
                strings.add(String.join("\n", subIngredients));
                startIndex = endIndex;
            }

            // initial embed
            EmbedBuilder embed = new EmbedBuilder();
            embed.setAuthor(sourceName + " (" + id + ")",sourceUrl)
                    .setTitle(title)
                    .addField("Total time:", readyInMinutes + " minutes", true)
                    .addField("Servings:", servings + " servings", true)
                    .addField("Calories:", calories + " calories", true)
                    .addField("Ingredients:", strings.get(0), true)
                    .addField("", strings.get(1), true)
                    .addField("", strings.get(2), true)
                    .setImage(image)
                    .setColor(new Color(255,255,255))
                    .setFooter("Made by @condyl#999")
                    .setTimestamp(new Date().toInstant());
            //event.getHook().sendMessage("").setEmbeds(embed.build()).queue();

            Page originalPage = new InteractPage(embed.build());
            pages.add(originalPage);


            String allInstructions = "";
            for(int i = 0; i < s.length(); i++){
                String step = s.getJSONObject(i).getString("step");
                allInstructions = allInstructions + "**Step " + (i+1) + ": **" + step + "\n\n";
            }
            EmbedBuilder stepEmbed = new EmbedBuilder();
            stepEmbed.setAuthor(sourceName,sourceUrl)
                    .setTitle(title + " (Instructions)")
                    .setDescription(allInstructions)
                    .setThumbnail(image)
                    .setColor(new Color(255,255,255))
                    .setFooter("Made by @condyl#999")
                    .setTimestamp(new Date().toInstant());

            Page instructionsPage = new InteractPage(stepEmbed.build());
            pages.add(instructionsPage);

            event.getHook().sendMessage("").setEmbeds(embed.build()).queue(success -> {
                Pages.paginate(success, pages, true);
            });
        }

        else if (command.equalsIgnoreCase("wimf")){
            List<Page> pages = new ArrayList<>();
            OptionMapping queryOption = event.getOption("ingredients");
            assert queryOption != null;
            String query = queryOption.getAsString();

            String editedQuery = query.replace(" ", "%20")
                    .replace(",", "%2C")
                    .replace(", ","%2C");

            // api stuff
            String link = "https://spoonacular-recipe-food-nutrition-v1.p.rapidapi.com/recipes/findByIngredients?ingredients="
                    + editedQuery;

            event.deferReply().queue();

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(link))
                    .header("X-RapidAPI-Key", key)
                    .header("X-RapidAPI-Host", "spoonacular-recipe-food-nutrition-v1.p.rapidapi.com")
                    .method("GET", HttpRequest.BodyPublishers.noBody())
                    .build();

            HttpResponse<String> response;
            try {
                response = HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString());
            } catch (IOException | InterruptedException e) {
                throw new RuntimeException(e);
            }

            JSONArray temp1 = new JSONArray(response.body());
            JSONObject dish = temp1.getJSONObject((int)(Math.random()*temp1.length()));
            System.out.println(dish);

            // get names of used, missed, and unused ingredients
            JSONArray usedIngredients = dish.getJSONArray("usedIngredients");
            String usedIngredientsNames = "";
            for(int i = 0; i < usedIngredients.length(); i++){
                JSONObject ingredient = usedIngredients.getJSONObject(i);
                usedIngredientsNames = usedIngredientsNames + "\n• " +  ingredient.getString("name").substring(0,1).toUpperCase() + ingredient.getString("name").substring(1).toLowerCase();
            }
            JSONArray missedIngredients = dish.getJSONArray("missedIngredients");
            String missedIngredientsNames = "";
            for(int i = 0; i < missedIngredients.length(); i++){
                JSONObject ingredient = missedIngredients.getJSONObject(i);
                missedIngredientsNames = missedIngredientsNames + "\n• " +  ingredient.getString("name").substring(0,1).toUpperCase() + ingredient.getString("name").substring(1).toLowerCase();
            }
            JSONArray unusedIngredients = dish.getJSONArray("unusedIngredients");
            String unusedIngredientsNames = "";
            for(int i = 0; i < unusedIngredients.length(); i++){
                JSONObject ingredient = unusedIngredients.getJSONObject(i);
                unusedIngredientsNames = unusedIngredientsNames + "\n• " +  ingredient.getString("name").substring(0,1).toUpperCase() + ingredient.getString("name").substring(1).toLowerCase();
            }

            String title = dish.getString("title");
            String image = dish.getString("image");
            int id = dish.getInt("id");
            link = "https://spoonacular-recipe-food-nutrition-v1.p.rapidapi.com/recipes/" + id + "/analyzedInstructions?stepBreakdown=true";

            request = HttpRequest.newBuilder()
                    .uri(URI.create(link))
                    .header("X-RapidAPI-Key", key)
                    .header("X-RapidAPI-Host", "spoonacular-recipe-food-nutrition-v1.p.rapidapi.com")
                    .method("GET", HttpRequest.BodyPublishers.noBody())
                    .build();

            HttpResponse<String> recipeInstructionsResponse;
            try {
                recipeInstructionsResponse = HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString());
            } catch (IOException | InterruptedException e) {
                throw new RuntimeException(e);
            }

            System.out.println(recipeInstructionsResponse.body());

            // get each step
            JSONArray temp2 = new JSONArray(recipeInstructionsResponse.body());
            JSONObject temp3 = temp2.getJSONObject(0);
            JSONArray steps = temp3.getJSONArray("steps");
            ArrayList<String> step = new ArrayList<>();
            for(int i = 0; i < steps.length(); i++){
                JSONObject currentStep = steps.getJSONObject(i);
                step.add(currentStep.getString("step"));
            }

            EmbedBuilder embed = new EmbedBuilder();
            embed.setAuthor("Recipe (" + id + ")")
                    .setTitle(title)
                    .addField("", "Found recipe using: " + query, false)
                    .addField("Used Ingredients:", usedIngredientsNames, true)
                    .addField("Missed Ingredients:", missedIngredientsNames, true)
                    .addField("Unused Ingredients:", unusedIngredientsNames, true)
                    .setImage(image)
                    .setColor(new Color(255,255,255))
                    .setFooter("Made by @condyl#999")
                    .setTimestamp(new Date().toInstant());
            //event.getHook().sendMessage("").setEmbeds(embed.build()).queue();

            Page originalPage = new InteractPage(embed.build());
            pages.add(originalPage);

            String allInstructions = "";
            for(int i = 0; i < step.size()-1; i++){
                allInstructions = allInstructions + "**Step " + (i+1) + ": **" + step.get(i) + "\n\n";
            }
            EmbedBuilder stepEmbed = new EmbedBuilder();
            stepEmbed.setAuthor("Recipe")
                    .setTitle(title + " (Instructions)")
                    .setDescription(allInstructions)
                    .setThumbnail(image)
                    .setColor(new Color(255,255,255))
                    .setFooter("Made by @condyl#999")
                    .setTimestamp(new Date().toInstant());


            Page instructionsPage = new InteractPage(stepEmbed.build());
            pages.add(instructionsPage);

            event.getHook().sendMessage("").setEmbeds(embed.build()).queue(success -> {
                Pages.paginate(success, pages, true);
            });
        }

        else if (command.equalsIgnoreCase("randomrecipe")){
            List<Page> pages = new ArrayList<>();
            OptionMapping tagsOption = event.getOption("tags");
            String tags = "";
            String editedTags = "";
            if(tagsOption!=null) {
                tags = tagsOption.getAsString();

                editedTags = tags.replace(" ", "%20")
                        .replace(",", "%2C")
                        .replace(", ", "%2C");
            }

            // api stuff
            String link = "https://spoonacular-recipe-food-nutrition-v1.p.rapidapi.com/recipes/random?number=1";
            if(tagsOption!=null){
                link = link + editedTags;
            }

            event.deferReply().queue();

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(link))
                    .header("X-RapidAPI-Key", key)
                    .header("X-RapidAPI-Host", "spoonacular-recipe-food-nutrition-v1.p.rapidapi.com")
                    .method("GET", HttpRequest.BodyPublishers.noBody())
                    .build();

            HttpResponse<String> response;
            try {
                response = HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString());
            } catch (IOException | InterruptedException e) {
                throw new RuntimeException(e);
            }

            JSONObject temp1 = new JSONObject(response.body());
            JSONArray temp2 = temp1.getJSONArray("recipes");
            JSONObject dish = new JSONObject();
            try {
                dish = temp2.getJSONObject(0);
            }
            catch (JSONException e){
                EmbedBuilder errorEmbed = new EmbedBuilder();
                errorEmbed.setTitle("Error: JSON Exception")
                        .setDescription("No recipes for tags *" + tags + "* could be found.  Try again.")
                        .setColor(new Color(255,0,0))
                        .setFooter("Made by @condyl#999")
                        .setTimestamp(new Date().toInstant());
                event.getHook().sendMessage("").setEmbeds(errorEmbed.build()).queue();
            }

            System.out.println(dish);

            // json stuff
            String title = dish.getString("title");
            String image = dish.getString("image");
            int id = dish.getInt("id");
            String sourceName = dish.getString("sourceName");
            String sourceUrl = dish.getString("sourceUrl");
            int servings = dish.getInt("servings");
            int readyInMinutes = dish.getInt("readyInMinutes");
            JSONArray dishTypesArray = dish.getJSONArray("dishTypes");
            String dishTypes = "";
            for(int i = 0; i < dishTypesArray.length(); i++){
                dishTypes = dishTypes + "\n• " + dishTypesArray.get(i);
            }

            EmbedBuilder embed = new EmbedBuilder();
            embed.setAuthor(sourceName + " (" + id + ")", sourceUrl)
                    .setTitle(title)
                    .addField("Total time:", readyInMinutes + " minutes", true)
                    .addField("Servings:", servings + " servings", true)
                    .addField("Dish types: ",dishTypes,false)
                    .setImage(image)
                    .setColor(new Color(255,255,255))
                    .setFooter("Made by @condyl#999")
                    .setTimestamp(new Date().toInstant());

            Page originalPage = new InteractPage(embed.build());
            pages.add(originalPage);

            JSONArray analyzedInstructions = dish.getJSONArray("analyzedInstructions");
            JSONObject stepsObj = analyzedInstructions.getJSONObject(0);
            JSONArray stepsArray = stepsObj.getJSONArray("steps");

            String instructions = "";
            for(int i = 0; i < stepsArray.length(); i++){
                JSONObject instruction = stepsArray.getJSONObject(i);
                instructions = instructions + "**Step " + (i+1) + ": **" + instruction.getString("step") + "\n\n";
            }

            EmbedBuilder stepEmbed = new EmbedBuilder();
            stepEmbed.setAuthor(sourceName,sourceUrl)
                    .setTitle(title + " (Instructions)")
                    .setDescription(instructions)
                    .setThumbnail(image)
                    .setColor(new Color(255,255,255))
                    .setFooter("Made by @condyl#999")
                    .setTimestamp(new Date().toInstant());

            Page instructionsPage = new InteractPage(stepEmbed.build());
            pages.add(instructionsPage);
















            event.getHook().sendMessage("").setEmbeds(embed.build()).queue(success -> {
                Pages.paginate(success, pages, true);
            });
        }


    }
    @Override
    public void onGuildReady(GuildReadyEvent event) {
        event.getGuild().upsertCommand("getrecipe", "get recipe for requested dish")
                .addOptions(
                        new OptionData(OptionType.STRING, "query", "the dish you want a recipe for", true)
                ).queue();
        event.getGuild().upsertCommand("wimf", "what's in my fridge? : get a dish that you can make using the ingredients in your fridge")
                .addOptions(
                        new OptionData(OptionType.STRING, "ingredients", "the ingredients you have, seperated by commas", true)
                ).queue();
        event.getGuild().upsertCommand("randomrecipe", "get recipe for a random dish")
                .addOptions(
                        new OptionData(OptionType.STRING, "tags", "ex. dessert, lunch, vegetarian, etc. : seperated by commas", false)
                ).queue();
    }

}
