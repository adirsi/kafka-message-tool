package application.root;

import application.MainApplication;
import application.constants.ApplicationConstants;
import application.customfxwidgets.CustomFxWidgetsLoader;
import application.customfxwidgets.mainviewcontroller.ControllerRepositoryFactory;
import application.customfxwidgets.mainviewcontroller.DefaultControllerRepositoryFactory;
import application.customfxwidgets.mainviewcontroller.MainApplicationController;
import application.globals.AppGlobals;
import application.kafka.KafkaClusterProxies;
import application.globals.StageRepository;
import application.kafka.ClusterStatusChecker;
import application.kafka.DefaultKafkaMessageSender;
import application.kafka.listener.KafkaListeners;
import application.logging.KafkaToolConsoleLogger;
import application.logging.GuiWindowedLogger;
import application.logging.AppLogger;
import application.logging.LogEventDataFormatter;
import application.model.DataModel;
import application.model.DefaultModelDataProxy;
import application.model.FromPojoConverter;
import application.model.ModelDataProxy;
import application.notifications.AppNotifier;
import application.persistence.ApplicationSettings;
import application.persistence.DefaultApplicationSettings;
import application.persistence.GlobalSettings;
import application.persistence.GuiSettings;
import application.persistence.XmlFileConfig;
import application.scripting.codearea.SyntaxHighlightingCodeAreaConfigurator;
import application.utils.ApplicationVersionProvider;
import application.utils.GuiUtils;
import application.utils.UserGuiInteractor;
import application.utils.kafka.KafkaProducers;
import javafx.application.Application;
import javafx.scene.Scene;
import javafx.stage.Stage;
import org.fxmisc.flowless.VirtualizedScrollPane;
import org.fxmisc.richtext.StyleClassedTextArea;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;


public class FxApplicationLogicRoot implements FxApplicationRoot {

    private static final String MAIN_APPLICATION_VIEW_FXML_FILE = "MainApplicationView.fxml";

    private ApplicationPorts applicationPorts;
    private Stage mainStage;
    private ApplicationSettings applicationSettings;
    private Scene scene;
    private ExecutorService executorService;
    private MainApplication mainApplication;

    public FxApplicationLogicRoot(MainApplication mainApplication) {
        this.mainApplication = mainApplication;
    }

    @Override
    public void start(Stage stage) throws Exception {

        initialize(stage);

        PrerequisiteChecker.assertPrerequisites();

        createObjects();
        configureScene();
        configureStage();

        applicationPorts.start();
        mainStage.show();
    }

    public void stopAll() {
        applicationSettings.save();
        applicationPorts.stop();
        StageRepository.closeAllStages();
        KafkaProducers.close();
        executorService.shutdown();
    }

    @Override
    public void stop() {
        stopAll();
    }

    @Override
    public Application getApplication() {
        return mainApplication;
    }

    private void initialize(Stage stage) {
        mainStage = stage;
        AppLogger.registerLogger(new KafkaToolConsoleLogger(new LogEventDataFormatter()));
        AppGlobals.initialize();
    }

    private void configureScene() {
        GuiUtils.loadCssIfPossible(scene, ApplicationConstants.GLOBAL_CSS_FILE_NAME);
        GuiUtils.loadCssIfPossible(scene, ApplicationConstants.GROOVY_KEYWORDS_STYLES_CSS);
        GuiUtils.loadCssIfPossible(scene, ApplicationConstants.JSON_STYLES_CSS);
        mainStage.setOnCloseRequest(event -> stopAll());
    }

    private void configureStage() {
        mainStage.setScene(scene);
        mainStage.setTitle(String.format(ApplicationConstants.APPLICATION_NAME + " (%s)", ApplicationVersionProvider.get()));
        GuiUtils.addApplicationIcon(mainStage);
    }

    private void createObjects() throws Exception {


        final DataModel dataModel = new DataModel();
        final GuiSettings guiSettings = new GuiSettings();
        final GlobalSettings globalSettings = new GlobalSettings();
        applicationPorts = new DefaultApplicationPorts(new DefaultKafkaMessageSender(),
                                                       new KafkaListeners());

        final ModelDataProxy modelDataProxy = new DefaultModelDataProxy(dataModel);
        final XmlFileConfig xmlFileConfig = new XmlFileConfig(modelDataProxy,
                                                              new FromPojoConverter(modelDataProxy),
                                                              guiSettings,
                                                              globalSettings);

        final StyleClassedTextArea loggingPaneArea = getStyleClassedTextArea();
        final VirtualizedScrollPane<StyleClassedTextArea> loggingPane = new VirtualizedScrollPane<>(loggingPaneArea);

        final UserGuiInteractor interactor = new UserGuiInteractor(mainStage);
        final ApplicationBusySwitcher busySwitcher = new DefaultApplicationBusySwitcher(mainStage);

        AppLogger.registerLogger(new GuiWindowedLogger(loggingPaneArea));
        applicationSettings = new DefaultApplicationSettings(xmlFileConfig);
        applicationSettings.load();
        AppLogger.setLogLevel(applicationSettings.appSettings().getLogLevel());

        executorService = Executors.newSingleThreadExecutor();
        final KafkaClusterProxies kafkaClusterProxies = new KafkaClusterProxies();
        final ControllerRepositoryFactory controllerRepositoryFactory =
            new DefaultControllerRepositoryFactory(new ClusterStatusChecker(busySwitcher, interactor, kafkaClusterProxies),
                                                   new SyntaxHighlightingCodeAreaConfigurator(executorService),
                                                   kafkaClusterProxies);

        final DefaultActionHandlerFactory actionHandlerFactory = new DefaultActionHandlerFactory(interactor,
                                                                                                 modelDataProxy,
                                                                                                 mainStage,
                                                                                                 applicationPorts);
        final MainApplicationController mainController = new MainApplicationController(mainStage,
                                                                                       dataModel,
                                                                                       getApplication(),
                                                                                       applicationSettings,
                                                                                       loggingPane,
                                                                                       controllerRepositoryFactory,
                                                                                       actionHandlerFactory,
                                                                                       busySwitcher,
                                                                                       AppNotifier.getStatusNotifier());
        CustomFxWidgetsLoader.load(mainController, MAIN_APPLICATION_VIEW_FXML_FILE);
        mainController.setupControls();

        scene = new Scene(mainController);


    }

    private StyleClassedTextArea getStyleClassedTextArea() {
        final StyleClassedTextArea loggingPaneArea = new StyleClassedTextArea();
        loggingPaneArea.setWrapText(true);
        loggingPaneArea.setEditable(false);
        return loggingPaneArea;
    }
}
