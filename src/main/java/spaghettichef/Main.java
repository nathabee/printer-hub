package spaghettichef;

import spaghettichef.central.CentralMain;
import spaghettichef.central.CentralMode;
import spaghettichef.local.LocalMain;

public final class Main {
    private Main() {
    }

    public static void main(String[] args) throws InterruptedException {
        if (CentralMode.enabled()) {
            CentralMain.startAndWait();
            return;
        }

        LocalMain.main(args);
    }
}
