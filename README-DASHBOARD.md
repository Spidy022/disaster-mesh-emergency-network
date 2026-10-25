# Dashboard

This project includes a Java Swing dashboard in `src/main/java/com/disastermesh/DashboardApp.java`.

From the folder containing `pom.xml`, run the console simulation as before:

```powershell
mvn clean test
mvn exec:java
```

To launch the GUI dashboard instead:

```powershell
mvn clean test
mvn exec:java -Dexec.mainClass=com.disastermesh.DashboardApp
```

The dashboard provides Start, Pause, Step, and Reset controls; a live node map; node battery/position/status table; relay metrics; and a risk history plot with the configured 0.40 handover threshold. It uses the same `SimulationEngine` and simulation parameters as the console app.
