package com.officialsounding.boutparse;

import com.github.davidmoten.geo.GeoHash;
import com.github.davidmoten.geo.LatLong;
import com.google.code.geocoder.Geocoder;
import com.google.code.geocoder.GeocoderRequestBuilder;
import com.google.code.geocoder.model.*;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.officialsounding.boutparse.model.Bout;
import com.officialsounding.boutparse.model.Team;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.sql.*;
import java.sql.Date;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.regex.Pattern;

/**
 * Created by Peter on 5/31/2014.
 */
public class BoutFetcher {

    private static final Logger log = LoggerFactory.getLogger(BoutFetcher.class);
    private final Geocoder geocoder = new Geocoder();

    public static void main(String[] args) throws Exception{

        Set<Integer> teamIds = new HashSet<>();
        Set<Integer> tournamentIds = new HashSet<>();

        BoutFetcher bf = new BoutFetcher();
        Gson g = new Gson();

        List<Bout> bouts;
        List<Team> teams;



        File boutout = new File("bouts.json");
        File teamout = new File("teams.json");

        if(boutout.exists()) {
            log.info("bout file exists, reading in");
            try(InputStreamReader br = new InputStreamReader(new FileInputStream(boutout))) {
                bouts = g.fromJson(br,new TypeToken<ArrayList<Bout>>() {}.getType());
            }
        } else {
            if(!boutout.createNewFile()) {
                   throw new IOException("failed to create bout file");
            }
            log.info("generating bout file from web");
            bouts = bf.getBoutList();


            try (OutputStreamWriter bo = new OutputStreamWriter(new FileOutputStream(boutout))) {
                String boutJson = g.toJson(bouts);
                bo.append(boutJson);
                bo.flush();
                log.info("bout file persisted");
            }
        }

        for(Bout b: bouts) {
            teamIds.add(b.getAwayID());
            teamIds.add(b.getHomeID());
            tournamentIds.add(b.getTournamentID());
        }

        log.info("found {} unique team ids and {} unique tournament ids",teamIds.size(),tournamentIds.size());


        if(teamout.exists()) {
            log.info("team file exists, reading in");
            try(InputStreamReader tr = new InputStreamReader(new FileInputStream(teamout))) {
                teams = g.fromJson(tr,new TypeToken<ArrayList<Team>>() {}.getType());
            }
        } else {
            log.info("generating team file from web");
            teamout.createNewFile();

            teams = bf.getTeamsByIDs(teamIds);
            try (OutputStreamWriter to = new OutputStreamWriter(new FileOutputStream(teamout))) {
                String teamJson = g.toJson(teams);
                to.append(teamJson);
                to.flush();
            }
        }


        log.info("persisting bouts and teams to database");

        bf.perist(bouts, teams, args[0]);
    }

    private void perist(List<Bout> bouts, List<Team> teams, String password) throws SQLException{

        String url = "jdbc:postgresql://localhost/boutfinder";
        Properties props = new Properties();
        props.setProperty("user","postgres");
        props.setProperty("password",password);
        Connection conn = DriverManager.getConnection(url, props);

        String insertBout = "INSERT INTO bout (home_team_id, away_team_id, date, sanctioned_by) values (?,?,?,?)";
        String insertTeam = "INSERT INTO team (id, name, location_text, lat, lng, url) values (?,?,?,?,?,?)";


        for(Team team: teams) {
            try(PreparedStatement ps = conn.prepareStatement(insertTeam)) {
                ps.setInt(1,team.getId());
                ps.setString(2,team.getName());
                ps.setString(3,team.getLocationName());
                if(team.getLocationCoords() != null) {
                    ps.setDouble(4, team.getLocationCoords().getLat());
                    ps.setDouble(5, team.getLocationCoords().getLon());
                } else {
                    ps.setDouble(4,0);
                    ps.setDouble(5,0);
                }

                ps.setString(6,team.getWebsite());

                ps.execute();
            }
        }

        for(Bout bout: bouts) {
            try(PreparedStatement ps = conn.prepareStatement(insertBout)) {
                ps.setInt(1,bout.getHomeID());
                ps.setInt(2,bout.getAwayID());
                ps.setDate(3,new Date(Date.from(bout.getDate().atStartOfDay().toInstant(ZoneOffset.UTC)).getTime()));
                ps.setString(4,bout.getSanctionedBy());

                ps.execute();
            }
        }
    }


    public List<Bout> getBoutList() throws IOException {

        log.info("Beginning Bout List retrieval");
        int maxPage = -1;
        int page = 0;

        List<Bout> bouts = new ArrayList<>();
        DateTimeFormatter frmt = DateTimeFormatter.ofPattern("M/d/yy");
        Pattern p = Pattern.compile("\\d+");

        do {
            Document doc = getDocument("http://flattrackstats.com/bouts/upcoming?page="+page);
            if(page == 0) {
                maxPage = Integer.parseInt(doc.select("li.pager-last > a").attr("href").replace("/bouts/upcoming?page=", ""));
                log.debug("Max page in upcoming bouts list is {}",maxPage);
            }

            log.debug("retrieved page {}",page);


            for(Element row: doc.select("table.views-table tr.upcoming")) {
                Bout b = new Bout();

                b.setDate(LocalDate.parse(row.select("td.views-field-field-bout-date-value").text(), frmt));
                b.setSanctionedBy(row.select("td.views-field-nid-2 > div").attr("tooltip"));
                b.setHomeID(Integer.parseInt(row.select("td.views-field-title a").attr("href").replaceAll("\\D+","")));
                b.setAwayID(Integer.parseInt(row.select("td.views-field-title-1 a").attr("href").replaceAll("\\D+","")));
                if(row.select("td.views-field-title-2").hasText()) {
                    b.setTournamentID(Integer.parseInt(row.select("td.views-field-title-2 a").attr("href").replaceAll("\\D+","")));
                }

                log.trace("found bout {}",b);
                bouts.add(b);
            }

            try {
                Thread.sleep(5000);     //be a good webcrawling citizen
            }catch(InterruptedException igr) {}
        }while(++page <= maxPage);

        log.info("Bout Retrieval finished, {} bouts found",bouts.size());
        return bouts;
    }

    public List<Team> getTeamsByIDs(Set<Integer> teamIDs) throws IOException {
        List<Team> teams = new ArrayList<>();
        Map<String,LatLong> cache = new HashMap<>();

        for(Integer teamID: teamIDs) {
            Document page = getDocument("http://flattrackstats.com/teams/" + teamID);

            Team t = new Team();
            t.setId(teamID);
            t.setName(page.select("div.leagueName").text());
            t.setWebsite(page.select("div.website > a").attr("href"));

            String locationText = page.select("div.vitals.stats div.value.large").first().text();
            t.setLocationName(locationText);

            if(cache.containsKey(locationText)) {
                log.debug("{} is in cache",locationText);
                t.setLocationCoords(cache.get(locationText));
                t.setLocationHash(GeoHash.encodeHash(t.getLocationCoords()));
            } else if(locationText.trim().length() > 0) {

                log.debug("geocoding team id {}'s location {}", teamID, locationText);
                GeocoderRequest geocoderRequest = new GeocoderRequestBuilder().setAddress(locationText).setLanguage("en").getGeocoderRequest();
                GeocodeResponse geocoderResponse = geocoder.geocode(geocoderRequest);

                List<GeocoderResult> results = geocoderResponse.getResults();

                log.debug("found {} possible geocoding results", results.size());

                if (results.size() > 0) {
                    LatLng result = results.get(0).getGeometry().getLocation();
                    t.setLocationCoords(new LatLong(result.getLat().doubleValue(), result.getLng().doubleValue()));
                    t.setLocationHash(GeoHash.encodeHash(t.getLocationCoords()));

                    log.debug("geocoded lat/long: {}, geohash: {}", result, t.getLocationHash());
                    cache.put(locationText,t.getLocationCoords());
                }


            }

            log.trace("found team {}",t);
            teams.add(t);

            try {
                Thread.sleep(5000);     //be a good webcrawling citizen
            }catch(InterruptedException igr) {}
        }


        return teams;
    }

    private Document getDocument(String URL) {
        Document doc = null;
        boolean retry = true;
        do {
            try {
                doc = Jsoup.connect(URL).get();
                retry = false;
            } catch (Exception igr) {
                log.debug("got exception getting page", igr);
            }
        }while(retry);

        return doc;
    }
}
