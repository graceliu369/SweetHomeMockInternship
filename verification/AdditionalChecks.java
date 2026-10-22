import com.eteks.sweethome3d.io.*;
import com.eteks.sweethome3d.model.*;
import com.eteks.sweethome3d.swing.*;
import com.eteks.sweethome3d.viewcontroller.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.awt.print.*;
import java.io.*;
import java.lang.reflect.Method;
import java.util.*;
import javax.swing.*;
import javax.swing.table.*;

/** Extra regression checks. Does not alter the provided assignment-tests. */
public class AdditionalChecks {
  private static final DefaultUserPreferences PREFS = new DefaultUserPreferences();
  private static void check(boolean ok, String message) {
    if (!ok) throw new AssertionError(message);
    System.out.println("PASS: " + message);
  }
  private static HomePrint settings(Float scale) {
    return new HomePrint(HomePrint.PaperOrientation.PORTRAIT, 612, 792,
        36, 36, 36, 36, true, true, false, scale, null, null);
  }
  private static Home threeLevels() {
    Home home = new Home();
    home.getCompass().setVisible(false);
    for (int i = 0; i < 3; i++) {
      Level level = new Level("Level " + i, i * 250f, 10f, 240f);
      home.addLevel(level);
      Wall wall = new Wall(0, 0, 600, 0, 10, 240);
      home.addWall(wall);
      wall.setLevel(level);
    }
    home.setSelectedLevel(home.getLevels().get(2));
    return home;
  }
  private static void checkPlan() throws Exception {
    Home home = threeLevels();
    Level selected = home.getSelectedLevel();
    PlanComponent plan = new PlanComponent(home, PREFS, null);
    BufferedImage image = new BufferedImage(800, 1000, BufferedImage.TYPE_INT_ARGB);
    Graphics2D graphics = image.createGraphics();
    PageFormat format = HomePrintableComponent.getPageFormat(settings(null));
    try {
      home.getLevels().get(1).setViewable(false);
      check(plan.print(graphics, format, 0) == Printable.PAGE_EXISTS
          && plan.print(graphics, format, 1) == Printable.PAGE_EXISTS
          && plan.print(graphics, format, 2) == Printable.NO_SUCH_PAGE,
          "hidden levels do not receive plan pages");
      check(home.getSelectedLevel() == selected, "selection restored after out-of-range page");
      home.getLevels().get(1).setViewable(true);
      home.setPrint(settings(1f / 10));
      // 600 cm at 1:10 is 1700 points: four horizontal pages per level.
      int count = 0;
      while (plan.print(graphics, format, count) == Printable.PAGE_EXISTS) count++;
      check(count == 12, "fixed-scale pages retain all tiles for all three levels");
      check(home.getSelectedLevel() == selected, "selection restored after fixed-scale printing");
      Method itemsMethod = PlanComponent.class.getDeclaredMethod("getSelectedLevelPrintedItems");
      itemsMethod.setAccessible(true);
      Wall unassigned = new Wall(900, 0, 1100, 0, 10, 240);
      home.addWall(unassigned);
      unassigned.setLevel(null);
      check(((java.util.List<?>)itemsMethod.invoke(plan)).contains(unassigned),
          "print bounds include unassigned objects that the painter shows");
      PlanComponent failing = new PlanComponent(home, PREFS, null) {
        @Override protected java.util.List<Selectable> getPaintedItems() {
          throw new IllegalStateException("simulated rendering failure");
        }
      };
      try {
        failing.print(graphics, format, 0);
        throw new AssertionError("failure injection was not reached");
      } catch (IllegalStateException expected) {
        check(home.getSelectedLevel() == selected, "selection restored when rendering throws");
      }
      home.setSelectedLevel(null);
      plan.print(graphics, format, 0);
      check(home.getSelectedLevel() == null, "null selected level restored");
      Home empty = new Home();
      empty.getCompass().setVisible(false);
      empty.addLevel(new Level("Empty", 0, 10, 240));
      PlanComponent emptyPlan = new PlanComponent(empty, PREFS, null);
      check(emptyPlan.print(graphics, format, 0) == Printable.PAGE_EXISTS
          && emptyPlan.print(graphics, format, 1) == Printable.NO_SUCH_PAGE,
          "empty viewable level still receives one plan page");
    } finally { graphics.dispose(); }
  }
  private static void checkDescriptionAndVolume() {
    Home home = new Home();
    CatalogPieceOfFurniture catalog = new CatalogPieceOfFurniture("Piece", null, null, 2, 3, 4, true, false);
    HomePieceOfFurniture piece = new HomePieceOfFurniture(catalog);
    home.addPieceOfFurniture(piece);
    home.setSelectedItems(Arrays.asList(piece));
    HomeFurnitureController editing = new HomeFurnitureController(home, PREFS, new SwingViewFactory(), null);
    editing.setDescription("A description saved by the controller");
    editing.modifyFurniture();
    check("A description saved by the controller".equals(piece.getDescription()),
        "description controller saves edits to furniture model");
    check(catalog.getVolume() == 24, "catalog volume uses all three dimensions");
    piece.setWidth(5);
    check(piece.getVolume() == 60, "volume recalculates after resizing");
    HomePieceOfFurniture larger = new HomePieceOfFurniture(new CatalogPieceOfFurniture("Large", null, null, 10, 10, 10, true, false));
    home.addPieceOfFurniture(larger);
    home.setFurnitureSortedProperty(HomePieceOfFurniture.SortableProperty.VOLUME);
    FurnitureTable table = new FurnitureTable(home, PREFS);
    check(table.getModel().getValueAt(0, 0) == piece, "table sorts volume ascending");
    home.setFurnitureDescendingSorted(true);
    check(table.getModel().getValueAt(0, 0) == larger, "table sorts volume descending");
  }
  private static boolean sameCamera(Camera actual, Camera expected) {
    return actual.getX() == expected.getX() && actual.getY() == expected.getY()
        && actual.getZ() == expected.getZ() && actual.getYaw() == expected.getYaw()
        && actual.getPitch() == expected.getPitch()
        && actual.getFieldOfView() == expected.getFieldOfView();
  }
  private static void checkPrintUiState() throws Exception {
    Home home = threeLevels();
    DefaultUserPreferences uiPreferences = new DefaultUserPreferences();
    uiPreferences.setLanguage("en");
    uiPreferences.setAerialViewCenteredOnSelectionEnabled(true);
    HomeController controller = new HomeController(home, uiPreferences, new SwingViewFactory() {
      @Override public HomeView createHomeView(Home h, UserPreferences p, HomeController c) {
        // Keep the real controllers/listeners while avoiding a native window in headless tests.
        return (HomeView)java.lang.reflect.Proxy.newProxyInstance(HomeView.class.getClassLoader(),
            new Class<?>[] {HomeView.class}, (proxy, method, arguments) -> {
              if (method.getReturnType() == Boolean.TYPE) return false;
              if (method.getReturnType() == java.util.List.class) return Collections.emptyList();
              return null;
            });
      }
    }, new FileContentManager(uiPreferences));
    controller.getView();
    controller.getHomeController3D();
    Level selectedLevel = home.getSelectedLevel();
    java.util.List<Wall> walls = new ArrayList<Wall>(home.getWalls());
    java.util.List<Selectable> selectedItems = Arrays.<Selectable>asList(walls.get(2));
    home.setSelectedItems(selectedItems);
    // Going down from level 2 clamps this height to the minimum; returning to
    // level 2 alone cannot undo that movement. The top camera may be moved by
    // both level-visibility and selection listeners as well.
    home.getObserverCamera().setZ(100);
    home.getTopCamera().setX(123);
    home.getTopCamera().setY(456);
    home.getTopCamera().setZ(987);
    Camera topCamera = home.getTopCamera().clone();
    Camera observerCamera = home.getObserverCamera().clone();
    PlanComponent plan = new PlanComponent(home, uiPreferences, controller.getPlanController());
    Graphics2D graphics = new BufferedImage(800, 1000, BufferedImage.TYPE_INT_ARGB).createGraphics();
    PageFormat format = HomePrintableComponent.getPageFormat(settings(null));
    try {
      check(plan.print(graphics, format, 0) == Printable.PAGE_EXISTS,
          "printing succeeds with real home-controller level listeners");
      check(home.getSelectedLevel() == selectedLevel && home.getSelectedItems().equals(selectedItems)
          && !home.isAllLevelsSelection(), "printing restores selected objects as well as their level");
      check(sameCamera(home.getTopCamera(), topCamera) && sameCamera(home.getObserverCamera(), observerCamera),
          "printing restores both camera positions after non-reversible listener changes");
      check(plan.print(graphics, format, 3) == Printable.NO_SUCH_PAGE,
          "out-of-range print page remains absent with controller listeners");
      check(home.getSelectedLevel() == selectedLevel && home.getSelectedItems().equals(selectedItems)
          && sameCamera(home.getTopCamera(), topCamera) && sameCamera(home.getObserverCamera(), observerCamera),
          "out-of-range printing restores objects, level, and both cameras");
      PlanComponent failing = new PlanComponent(home, uiPreferences, controller.getPlanController()) {
        @Override protected java.util.List<Selectable> getPaintedItems() {
          throw new IllegalStateException("simulated rendering failure with controller listeners");
        }
      };
      try {
        failing.print(graphics, format, 0);
        throw new AssertionError("controller-listener failure injection was not reached");
      } catch (IllegalStateException expected) {
        check(home.getSelectedLevel() == selectedLevel && home.getSelectedItems().equals(selectedItems)
            && !home.isAllLevelsSelection() && sameCamera(home.getTopCamera(), topCamera)
            && sameCamera(home.getObserverCamera(), observerCamera),
            "rendering failure restores objects, selection mode, level, and both cameras");
      }
      home.setAllLevelsSelection(true);
      selectedItems = Arrays.<Selectable>asList(walls.get(0), walls.get(2));
      home.setSelectedItems(selectedItems);
      plan.print(graphics, format, 1);
      check(home.isAllLevelsSelection() && home.getSelectedItems().equals(selectedItems),
          "printing preserves an existing selection across multiple levels");
    } finally { graphics.dispose(); }
  }
  private static class FailingTable extends FurnitureTable {
    FailingTable(Home home) { super(home, PREFS); }
    @Override public Printable getPrintable(JTable.PrintMode mode, java.text.MessageFormat header,
                                             java.text.MessageFormat footer) {
      return new Printable() {
        public int print(Graphics g, PageFormat format, int page) throws PrinterException {
          boolean level = false;
          for (int c = 0; c < getColumnCount(); c++)
            level |= getColumnModel().getColumn(c).getIdentifier() == HomePieceOfFurniture.SortableProperty.LEVEL;
          check(level, "printed table includes Level even when hidden on screen");
          check(getRowCount() == 2, "printed table includes furniture from every viewable level");
          throw new PrinterException("simulated printer failure");
        }
      };
    }
  }
  private static void checkTableFailure() throws Exception {
    Home home = threeLevels();
    for (int i = 0; i < 3; i++) {
      HomePieceOfFurniture piece = new HomePieceOfFurniture(new CatalogPieceOfFurniture("Piece "+i, null, null, 2, 3, 4, true, false));
      home.addPieceOfFurniture(piece);
      piece.setLevel(home.getLevels().get(i));
    }
    home.getLevels().get(1).setViewable(false);
    FailingTable table = new FailingTable(home);
    TableColumnModel oldColumns = table.getColumnModel();
    java.util.List<HomePieceOfFurniture.SortableProperty> oldProperties = home.getFurnitureVisibleProperties();
    Color oldGrid = table.getGridColor();
    Graphics2D graphics = new BufferedImage(800, 1000, BufferedImage.TYPE_INT_ARGB).createGraphics();
    try {
      table.print(graphics, HomePrintableComponent.getPageFormat(settings(null)), 0);
      throw new AssertionError("printer failure was not reached");
    } catch (PrinterException expected) {
      check(table.getColumnModel() == oldColumns, "original screen columns restored after printer error");
      check(home.getFurnitureVisibleProperties().equals(oldProperties), "home visible-column preferences unchanged");
      check(table.getGridColor().equals(oldGrid), "table grid restored after printer error");
    } finally { graphics.dispose(); }
    FurnitureTable.FurnitureFilter originalFilter = new FurnitureTable.FurnitureFilter() {
      public boolean include(Home h, HomePieceOfFurniture piece) { return true; }
    };
    table.setFurnitureFilter(originalFilter);
    HomeController controller = new HomeController(home, PREFS, new SwingViewFactory() {
      @Override public View createFurnitureView(Home h, UserPreferences p, FurnitureController c) {
        return table;
      }
    }, new FileContentManager(PREFS));
    graphics = new BufferedImage(800, 1000, BufferedImage.TYPE_INT_ARGB).createGraphics();
    try {
      new HomePrintableComponent(home, controller, new Font("SansSerif", Font.PLAIN, 12))
          .print(graphics, HomePrintableComponent.getPageFormat(settings(null)), 0);
      throw new AssertionError("coordinated printer failure was not reached");
    } catch (PrinterException expected) {
      check(table.getFurnitureFilter() == originalFilter, "original furniture filter restored after coordinated print error");
      check(table.getColumnModel() == oldColumns, "screen columns restored after coordinated print error");
    } finally { graphics.dispose(); }
  }
  private static void printSample(String output) throws Exception {
    Home sample = new HomeFileRecorder().readHome("samples/MultiLevelHome.sh3d");
    sample.setPrint(settings(null));
    sample.setName("MultiLevelHome.sh3d");
    HomeController controller = new HomeController(sample, PREFS, new SwingViewFactory(), new FileContentManager(PREFS));
    FurnitureTable table = (FurnitureTable)controller.getFurnitureController().getView();
    table.setSize(table.getPreferredSize());
    table.doLayout();
    table.getTableHeader().setSize(table.getWidth(), table.getTableHeader().getPreferredSize().height);
    TableColumnModel columns = table.getColumnModel();
    java.util.List<HomePieceOfFurniture.SortableProperty> properties = new ArrayList<>(sample.getFurnitureVisibleProperties());
    Level selected = sample.getSelectedLevel();
    HomePrintableComponent printable = new HomePrintableComponent(sample, controller, new Font("SansSerif", Font.PLAIN, 12));
    check(printable.getPageCount() == 4, "sample output contains furniture page and three plan pages");
    try (OutputStream stream = new FileOutputStream(output)) {
      new HomePDFPrinter(sample, PREFS, controller, new Font("SansSerif", Font.PLAIN, 12)).write(stream);
    }
    check(sample.getSelectedLevel() == selected, "sample export restores original level");
    check(table.getColumnModel() == columns && sample.getFurnitureVisibleProperties().equals(properties),
        "sample export leaves screen Level-column visibility unchanged");
    System.out.println("PDF: " + output);
  }
  public static void main(String[] args) throws Exception {
    System.setProperty("com.eteks.sweethome3d.no3D", "true");
    PREFS.setLanguage("en");
    final Throwable[] failure = {null};
    SwingUtilities.invokeAndWait(() -> {
      try {
        checkDescriptionAndVolume();
        checkPlan();
        checkPrintUiState();
        checkTableFailure();
        printSample(args[0]);
      } catch (Throwable ex) { failure[0] = ex; }
    });
    if (failure[0] != null) { failure[0].printStackTrace(); System.exit(1); }
    System.out.println("ALL ADDITIONAL CHECKS PASSED");
    System.exit(0);
  }
}
