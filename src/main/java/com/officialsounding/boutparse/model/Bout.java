package com.officialsounding.boutparse.model;

import java.io.Serializable;
import java.time.LocalDate;

/**
 * Created by Peter on 5/31/2014.
 */
public class Bout implements Serializable {

    private LocalDate date;
    private String sanctionedBy;
    private int homeID;
    private int awayID;
    private int tournamentID = -1;

    public LocalDate getDate() {
        return date;
    }

    public String getSanctionedBy() {
        return sanctionedBy;
    }

    public void setSanctionedBy(String sanctionedBy) {
        this.sanctionedBy = sanctionedBy;
    }

    public void setDate(LocalDate date) {
        this.date = date;
    }

    public int getHomeID() {
        return homeID;
    }

    public void setHomeID(int homeID) {
        this.homeID = homeID;
    }

    public int getAwayID() {
        return awayID;
    }

    public void setAwayID(int awayID) {
        this.awayID = awayID;
    }

    public int getTournamentID() {
        return tournamentID;
    }

    public void setTournamentID(int tournamentID) {
        this.tournamentID = tournamentID;
    }

    public String toString() {
        StringBuilder sb = new StringBuilder("{Bout: ");
        sb.append(homeID);
        sb.append(" vs ");
        sb.append(awayID);
        sb.append(" on ").append(date.toString());
        if(tournamentID > 0) {
            sb.append("at tournament").append(tournamentID);
        }

        return sb.toString();
    }
}
