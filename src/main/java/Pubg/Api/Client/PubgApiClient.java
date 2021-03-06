package Pubg.Api.Client;

import Database.DatabaseConnector;
import Pubg.Api.Models.Player;
import Pubg.Api.Models.PlayerSeason.GameModeStats;
import Pubg.Api.Models.PlayerSeason.PlayerSeason;
import Pubg.Api.Models.Season.Season;
import Util.ConfigHandler;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.reflect.TypeToken;

import java.io.InputStreamReader;
import java.lang.reflect.Type;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public class PubgApiClient {
    private HttpURLConnection connection;
    private String apiKey = ConfigHandler.getBotConfig("api.key");
    private String API_BASE_URL = "https://api.pubg.com/shards/steam/";
    private JsonParser parser = new JsonParser();
    private DatabaseConnector db = new DatabaseConnector();

    /*
     * Retrieve the player ID which is used as the key in all other API calls.
     * Pubg API allows you to search up to 6 players at once, I'm just assuming we will always do 1
     * and skipping implementing the ability to search for many.
     */
    private String getPlayerIdFromName(String playerName) throws Exception {
        if (db.isAccountStored(playerName)) {
            return db.getAccountId(playerName);
        }
        URL url = new URL(API_BASE_URL + "players?filter[playerNames]=" + playerName);
        activateConnection(url);

        JsonObject playerJson = (JsonObject) parser.parse(new InputStreamReader(connection.getInputStream(), StandardCharsets.UTF_8));
        Type listType = new TypeToken<ArrayList<Player>>() {}.getType();
        List<Player> players = new Gson().fromJson(playerJson.get("data"), listType);
        String playerId = players.get(0).getId();

        //Store the player info in the db for future use.
        if (!playerId.isEmpty()) {
            db.storeAccount(playerName, playerId);
        }
        return playerId;
    }

    /*
     * Retrieve the current season ID used in getting player stats.
     * Assumes we don't care about our past. Because we are always moving forward
     * getting better, faster, stronger. We are great men.
     */
    private String getLatestSeasonId() throws Exception {
        URL url = new URL(API_BASE_URL + "seasons");
        activateConnection(url);

        JsonObject seasonJson = (JsonObject) parser.parse(new InputStreamReader(connection.getInputStream(), StandardCharsets.UTF_8));
        Type listType = new TypeToken<ArrayList<Season>>(){}.getType();
        List<Season> seasons = new Gson().fromJson(seasonJson.get("data"), listType);

        for (Season season : seasons) {
            if (season.getSeasonAttributes().getIsCurrentSeason()) {
                return season.getId();
            }
        }
        throw new Exception("No current season found.");
    }

    private PlayerSeason getPlayerSeason(String playerName) throws Exception {
        String playerId = getPlayerIdFromName(playerName);
        String seasonId = getLatestSeasonId();
        String urlPath = "players/" +
                playerId +
                "/seasons/" +
                seasonId;

        URL url = new URL(API_BASE_URL + urlPath);
        activateConnection(url);
        JsonObject playerSeasonJson = (JsonObject) parser.parse(new InputStreamReader(connection.getInputStream(), StandardCharsets.UTF_8));
        return new Gson().fromJson(playerSeasonJson.get("data"), PlayerSeason.class);
    }

    public GameModeStats getDuosStatsForPlayer(String playerName) throws Exception {
        return getPlayerSeason(playerName).getPlayerSeasonAttributes().getGameModeHandler().getDuos();
    }

    public GameModeStats getSquadsForPlayer(String playerName) throws Exception {
        return getPlayerSeason(playerName).getPlayerSeasonAttributes().getGameModeHandler().getSquads();
    }

    private void activateConnection(URL url) throws Exception {
        connection = (HttpURLConnection) url.openConnection();
        connection.setRequestMethod("GET");
        connection.setRequestProperty("Authorization", apiKey);
        connection.setRequestProperty("Accept", "application/vnd.api+json");
        connection.connect();
    }
}
