package actors;

import javax.inject.Inject;

import akka.actor.ActorRef;
import akka.actor.UntypedActor;
import play.libs.akka.InjectedActorSupport;

/**
 * Created by vasek on 19. 11. 2016.
 */
public class DashboardParentActor extends UntypedActor implements InjectedActorSupport {

    public static class Create {
        private String hash;
        private ActorRef out;

        public Create(String hash, ActorRef out) {
            this.hash = hash;
            this.out = out;
        }
    }

    private DashboardActor.Factory childFactory;

    @Inject
    public DashboardParentActor(DashboardActor.Factory childFactory) {
        this.childFactory = childFactory;
    }

    @Override
    public void onReceive(Object message) throws Exception {
        if (message instanceof DashboardParentActor.Create) {
            DashboardParentActor.Create create = (DashboardParentActor.Create) message;
            ActorRef child = injectedChild(() -> childFactory.create(create.out), "dashboardActor-" + create.hash);
            sender().tell(child, self());
        }
    }

}
