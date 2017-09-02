package controllers;

import pojo.Dashboard;
import actors.DashboardParentActor;
import actors.UserParentActor;
import akka.NotUsed;
import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import akka.actor.Status;
import akka.japi.Pair;
import akka.stream.Materializer;
import akka.stream.OverflowStrategy;
import akka.stream.javadsl.*;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import controllers.forms.CreateDashboardItems;
import controllers.forms.CreateDashboardNew;
import controllers.forms.CreateDashboardOwner;
import controllers.forms.Item;
import org.apache.commons.collections4.IteratorUtils;
import org.apache.commons.lang3.StringUtils;
import org.reactivestreams.Publisher;
import org.slf4j.Logger;
import play.api.libs.Crypto;
import play.data.Form;
import play.data.FormFactory;
import play.libs.F;
import play.libs.Json;
import play.libs.concurrent.HttpExecutionContext;
import play.mvc.*;
import scala.compat.java8.FutureConverters;
import util.DashboardsRepository;
import views.html.createDashboardItems;
import views.html.index;
import views.html.old_dashboard;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.stream.Collectors;

import static akka.pattern.Patterns.ask;


@Singleton
public class Application extends Controller {

    private Logger logger = org.slf4j.LoggerFactory.getLogger("controllers.Application");

    public static final String SESSION_DASHBOARD_ITEMS = "items";
    public static final String SESSION_DASHBOARD_ITEMS_ITEMS = "items";
    public static final String SESSION_DASHBOARD_OWNER_NAME = "ownerName";
    public static final String SESSION_DASHBOARD_OWNER_EMAIL = "ownerEmail";
    public static final String SESSION_DASHBOARD_NAME = "name";
    public static final String SESSION_DASHBOARD_DESCRIPTION = "description";
    public static final String SESSION_DASHBOARD_TYPE = "type";

    public static long TIMEOUT_MILLIS = 100;

    private ActorRef dashboardParentActor;
    private ActorRef userParentActor;
    private Materializer materializer;
    private ActorSystem actorSystem;
    private FormFactory formFactory;

    private ObjectMapper objectMapper = new ObjectMapper();

    @Inject
    HttpExecutionContext ec;


    @Inject
    public Application(ActorSystem actorSystem,
                       Materializer materializer,
                       FormFactory formFactory,
                       @Named("dashboardParentActor") ActorRef dashboardParentActor,
                       @Named("userParentActor") ActorRef userParentActor) {
        this.dashboardParentActor = dashboardParentActor;
        this.userParentActor = userParentActor;
        this.materializer = materializer;
        this.actorSystem = actorSystem;
        this.formFactory = formFactory;
    }

    public Result showNewDashboard() {
        Form<CreateDashboardNew> createDashboard1Form = formFactory.form(CreateDashboardNew.class);
        Form<CreateDashboardNew> filledForm = createDashboard1Form.fill(
                new CreateDashboardNew(
                        session(SESSION_DASHBOARD_NAME),
                        session(SESSION_DASHBOARD_DESCRIPTION),
                        session(SESSION_DASHBOARD_TYPE)
                ));
        return ok(views.html.createDashboardNew.render(filledForm));
    }

    public Result processNewDashboard() {
        CreateDashboardNew createDashboardNewForm = formFactory.form(CreateDashboardNew.class).bindFromRequest(request()).get();
        session(SESSION_DASHBOARD_NAME, createDashboardNewForm.getName());
        session(SESSION_DASHBOARD_DESCRIPTION, createDashboardNewForm.getDescription());
        session(SESSION_DASHBOARD_TYPE, createDashboardNewForm.getType());
        final String sessionItems = session(SESSION_DASHBOARD_ITEMS);
        Form<CreateDashboardItems> createDashboard2Form = sessionItems != null ? formFactory.form(CreateDashboardItems.class).bind(Json.parse(sessionItems)) : formFactory.form(CreateDashboardItems.class);
        return ok(createDashboardItems.render(createDashboard2Form));

    }

    public Result addItem() {
        Item addItem = formFactory.form(Item.class).bindFromRequest(request()).get();
        ArrayNode itemsArray;

        if (session(SESSION_DASHBOARD_ITEMS) == null) {
            itemsArray = Json.newObject().putArray(SESSION_DASHBOARD_ITEMS_ITEMS).add(addItem.getItemName());
        } else {
            itemsArray = (ArrayNode)Json.parse(session(SESSION_DASHBOARD_ITEMS)).get(SESSION_DASHBOARD_ITEMS_ITEMS);
            itemsArray.add(addItem.getItemName());
        }


        itemsArray = Json.newArray().addAll(IteratorUtils.toList(itemsArray.elements()).stream().filter(node -> StringUtils.isNotBlank(node.asText())).distinct().collect(Collectors.toList()));
        JsonNode jsonItems = Json.newObject().set(SESSION_DASHBOARD_ITEMS, itemsArray);
        session(SESSION_DASHBOARD_ITEMS, jsonItems.toString());

        return showDashboardItems();
    }

    public Result showDashboardItems() {
        Form<CreateDashboardItems> createDashboard2Form = formFactory.form(CreateDashboardItems.class);
        try {
            Form<CreateDashboardItems> createDashboard2FormFilled = createDashboard2Form.fill(objectMapper.readValue(session(SESSION_DASHBOARD_ITEMS), CreateDashboardItems.class));
            return ok(createDashboardItems.render(createDashboard2FormFilled));
        } catch (IOException e) {
            logger.error(e.getMessage(), e);
            return ok(createDashboardItems.render(createDashboard2Form));
        }
    }

    public Result removeItem() {
        Item removeItem = formFactory.form(Item.class).bindFromRequest(request()).get();
        ArrayNode itemsArray = (ArrayNode)Json.parse(session(SESSION_DASHBOARD_ITEMS)).get(SESSION_DASHBOARD_ITEMS);

        List updateItems = IteratorUtils.toList(itemsArray.elements()).stream().filter(item -> !removeItem.getItemName().equals(item.asText())).collect(Collectors.toList());
        JsonNode jsonItems = Json.newObject().set(SESSION_DASHBOARD_ITEMS, Json.newArray().addAll(updateItems));
        session(SESSION_DASHBOARD_ITEMS, jsonItems.toString());

        return showDashboardItems();

    }

    public Result showDashboardOwner() {
        Form<CreateDashboardOwner> createDashboard3Form = formFactory.form(CreateDashboardOwner.class);
        Form<CreateDashboardOwner> filledForm = createDashboard3Form.fill(
                new CreateDashboardOwner(
                        session(SESSION_DASHBOARD_OWNER_NAME),
                        session(SESSION_DASHBOARD_OWNER_EMAIL)
                ));
        return ok(views.html.createDashboardOwner.render(filledForm));
    }

    public Result processDashboardOwner() {
        CreateDashboardOwner createDashboard3Form = formFactory.form(CreateDashboardOwner.class).bindFromRequest(request()).get();
        session(SESSION_DASHBOARD_OWNER_NAME, createDashboard3Form.getOwnerName());
        session(SESSION_DASHBOARD_OWNER_EMAIL, createDashboard3Form.getOwnerEmail());

        Dashboard dashboard = new Dashboard();
        dashboard.setName(session(SESSION_DASHBOARD_ITEMS));
        dashboard.setDescription(session(SESSION_DASHBOARD_DESCRIPTION));
        dashboard.setType(session(SESSION_DASHBOARD_TYPE));
        ArrayNode itemsNodes = (ArrayNode) Json.parse(session(SESSION_DASHBOARD_ITEMS)).get(SESSION_DASHBOARD_ITEMS_ITEMS);
        dashboard.setItems(IteratorUtils.toList(itemsNodes.elements()).stream().map(node -> node.asText()).collect(Collectors.toList()));
        dashboard.setOwnerName(session(SESSION_DASHBOARD_OWNER_NAME));
        dashboard.setOwnerEmail(session(SESSION_DASHBOARD_OWNER_EMAIL));

        DashboardsRepository.dashboards().insert(dashboard);

        return showCreatedDashboard();
    }

    private Result showCreatedDashboard() {
        return ok(views.html.createdDashboard.render());
    }


    public Result index() {
        return ok(index.render("Your new application is ready."));
    }


    public WebSocket ws(String hash) {
        return WebSocket.Json.acceptOrResult(request -> {
            if (sameOriginCheck(request)) {
                final CompletionStage<Flow<JsonNode, JsonNode, NotUsed>> future = wsFutureFlow(request, hash);
                final CompletionStage<F.Either<Result, Flow<JsonNode, JsonNode, ?>>> stage = future.thenApplyAsync(F.Either::Right);
                return stage.exceptionally(this::logException);
            } else {
                return forbiddenResult();
            }
        });
    }

    private CompletionStage<F.Either<Result, Flow<JsonNode, JsonNode, ?>>> forbiddenResult() {
        final Result forbidden = Results.forbidden("forbidden");
        final F.Either<Result, Flow<JsonNode, JsonNode, ?>> left = F.Either.Left(forbidden);

        return CompletableFuture.completedFuture(left);
    }


    public boolean sameOriginCheck(Http.RequestHeader rh) {
        final String origin = rh.getHeader("Origin");

        if (origin == null) {
            logger.error("originCheck: rejecting request because no Origin header found");
            return false;
        } else if (originMatches(origin)) {
            logger.debug("originCheck: originValue = " + origin);
            return true;
        } else {
            logger.error("originCheck: rejecting request because Origin header value " + origin + " is not in the same origin");
            return false;
        }
    }


    public CompletionStage<Flow<JsonNode, JsonNode, NotUsed>> wsFutureFlow(Http.RequestHeader request, String hash) {
        // create an actor ref source and associated publisher for sink
        final Pair<ActorRef, Publisher<JsonNode>> pair = createWebSocketConnections();
        ActorRef webSocketOut = pair.first();
        Publisher<JsonNode> webSocketIn = pair.second();

        String id = String.valueOf(request._underlyingHeader().id());
        // Create a user actor off the request id and attach it to the source
        final CompletionStage<ActorRef> userActorFuture = createUserActor(id, webSocketOut, hash);

        // Once we have an actor available, create a flow...
        final CompletionStage<Flow<JsonNode, JsonNode, NotUsed>> stage = userActorFuture
                .thenApplyAsync(userActor -> createWebSocketFlow(webSocketIn, userActor));

        return stage;
    }

    public CompletionStage<ActorRef> createUserActor(String id, ActorRef webSocketOut, String hash) {
        // Use guice assisted injection to instantiate and configure the child actor.
//        return FutureConverters.toJava(
//                ask(userParentActor, new UserParentActor.Create(id, webSocketOut), timeoutMillis)
//        ).thenApply(stageObj -> (ActorRef) stageObj);
        return FutureConverters.toJava(
                ask(userParentActor, new UserParentActor.Create(id, webSocketOut, hash), TIMEOUT_MILLIS)
        ).thenApply(stageObj -> (ActorRef) stageObj);
    }

    public CompletionStage<ActorRef> createDashboardActor(String name) {

        final String hash = Crypto.crypto().generateToken();

        // Use guice assisted injection to instantiate and configure the child actor.
        return FutureConverters.toJava(
                ask(dashboardParentActor, new DashboardParentActor.Create(name, hash), TIMEOUT_MILLIS)
        ).thenApply(stageObj -> (ActorRef) stageObj);
    }

    public Flow<JsonNode, JsonNode, NotUsed> createWebSocketFlow(Publisher<JsonNode> webSocketIn, ActorRef userActor) {
        // http://doc.akka.io/docs/akka/current/scala/stream/stream-flows-and-basics.html#stream-materialization
        // http://doc.akka.io/docs/akka/current/scala/stream/stream-integrations.html#integrating-with-actors

        // source is what comes in: browser ws events -> play -> publisher -> userActor
        // sink is what comes out:  userActor -> websocketOut -> play -> browser ws events
        final Sink<JsonNode, NotUsed> sink = Sink.actorRef(userActor, new Status.Success("success"));
        final Source<JsonNode, NotUsed> source = Source.fromPublisher(webSocketIn);
        final Flow<JsonNode, JsonNode, NotUsed> flow = Flow.fromSinkAndSource(sink, source);

        // Unhook the user actor when the websocket flow terminates
        // http://doc.akka.io/docs/akka/current/scala/stream/stages-overview.html#watchTermination
        return flow.watchTermination((ignore, termination) -> {
            termination.whenComplete((done, throwable) -> {
                logger.info("Terminating actor {}", userActor);
                //dashboardActor.tell(new Stock.Unwatch(null), userActor);
                actorSystem.stop(userActor);
            });

            return NotUsed.getInstance();
        });
    }


    public Pair<ActorRef, Publisher<JsonNode>> createWebSocketConnections() {
        // Creates a source to be materialized as an actor reference.

        // Creating a source can be done through various means, but here we want
        // the source exposed as an actor so we can send it messages from other
        // actors.
        final Source<JsonNode, ActorRef> source = Source.actorRef(10, OverflowStrategy.dropTail());

        // Creates a sink to be materialized as a publisher.  Fanout is false as we only want
        // a single subscriber here.
        final Sink<JsonNode, Publisher<JsonNode>> sink = Sink.asPublisher(AsPublisher.WITHOUT_FANOUT);

        // Connect the source and sink into a flow, telling it to keep the materialized values,
        // and then kicks the flow into existence.
        final Pair<ActorRef, Publisher<JsonNode>> pair = source.toMat(sink, Keep.both()).run(materializer);
        return pair;
    }


    public F.Either<Result, Flow<JsonNode, JsonNode, ?>> logException(Throwable throwable) {
        // https://docs.oracle.com/javase/tutorial/java/generics/capture.html
        logger.error("Cannot create websocket", throwable);
        Result result = Results.internalServerError("error");
        return F.Either.Left(result);
    }


    private boolean originMatches(String origin) {
        return origin.contains("localhost:9000") || origin.contains("localhost:19001");
    }

    public Result dashboard(String hash) {

        try {
//            String hash = "abc";
            ActorRef dashboardActor = (ActorRef) FutureConverters.toJava(
                    ask(dashboardParentActor, new DashboardParentActor.GetDashboard(hash), Application.TIMEOUT_MILLIS)
            ).toCompletableFuture().get();
            String name = (String)FutureConverters.toJava(
                    ask(dashboardActor, new Dashboard.GetName(), Application.TIMEOUT_MILLIS)).toCompletableFuture().get();
            return ok(old_dashboard.render(hash, name));
        } catch (Exception e) {
            e.printStackTrace();
            return internalServerError();
        }

    }

    public CompletionStage<Result> create() {

        DashboardForm dashboardForm = formFactory.form(DashboardForm.class).bindFromRequest().get();

        return CompletableFuture.supplyAsync(
                () -> createDashboardActor(dashboardForm.getName()), ec.current())
                .thenComposeAsync(dashboardActorFuture -> dashboardActorFuture
                        .thenComposeAsync(dashboardActor -> FutureConverters.toJava(ask(dashboardActor, new Dashboard.GetHash(), TIMEOUT_MILLIS))
                                .thenApplyAsync(hash -> dashboard((String) hash), ec.current()), ec.current()), ec.current());

    }

//    public Result addItem() {
//        ItemForm itemForm = formFactory.form(ItemForm.class).bindFromRequest().get();
//
//        try {
//            ActorRef dashboardActor = (ActorRef) FutureConverters.toJava(
//                    ask(dashboardParentActor, new DashboardParentActor.GetDashboard(itemForm.getHash()), Application.TIMEOUT_MILLIS)
//            ).toCompletableFuture().get();
//
//            ask(dashboardActor, new Dashboard.Item(itemForm.getName()), Application.TIMEOUT_MILLIS);
//        } catch (Exception e) {
//            e.printStackTrace();
//            return internalServerError();
//        }
//
//        return dashboard(itemForm.getHash());
//    }



}

