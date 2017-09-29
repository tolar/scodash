package controllers;

import javax.inject.Inject;

import akka.actor.ActorRef;
import akka.actor.UntypedActor;
import play.libs.akka.InjectedActorSupport;

/**
 * Created by vasek on 19. 11. 2016.
 */
public class UserParentActor extends UntypedActor implements InjectedActorSupport {

    public static class Create {
        private String id;
        private ActorRef out;
        private String hash;

        @Inject
        public Create(String id, ActorRef out, String hash) {
            this.id = id;
            this.out = out;
            this.hash = hash;
            //dashboardActor = actorSystem.actorSelection("/dashboardActor/" + dashboardHash).anchor();
        }
    }

    private UserActor.Factory childFactory;

    @Inject
    public UserParentActor(UserActor.Factory childFactory) {
        this.childFactory = childFactory;
    }

    @Override
    public void onReceive(Object message) throws Exception {
        if (message instanceof UserParentActor.Create) {
            UserParentActor.Create create = (UserParentActor.Create) message;
            ActorRef child = injectedChild(() -> childFactory.create(create.hash, create.out), "userActor-" + create.id);
            sender().tell(child, self());
        }
    }

}
