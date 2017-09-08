package util;

import play.Play;
import pojo.Dashboard;
import uk.co.panaxiom.playjongo.PlayJongo;

public class DashboardsRepository {

    public static PlayJongo jongo = Play.application().injector().instanceOf(PlayJongo.class);

    public static org.jongo.MongoCollection dashboards() {
        return jongo.getCollection("dashboards");
    }

    public void insert(Dashboard dashboard) {
        dashboards().insert(dashboard);
    }
}
