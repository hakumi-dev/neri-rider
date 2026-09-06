package dev.hakumi.neri;

import javax.xml.parsers.DocumentBuilderFactory;
import java.nio.file.Path;
import java.util.Objects;

public final class NeriFileTypeTest {
    public static void main(String[] args) throws Exception {
        var descriptor = DocumentBuilderFactory.newInstance().newDocumentBuilder()
            .parse(Path.of(args[0]).toFile());
        var registrations = descriptor.getElementsByTagName("fileType");
        var groups = descriptor.getElementsByTagName("group");
        org.w3c.dom.Element menu = null;
        for (int i = 0; i < groups.getLength(); i++) {
            var group = (org.w3c.dom.Element) groups.item(i);
            if (group.getAttribute("id").equals("Neri.Tools")) menu = group;
        }
        if (menu == null || !menu.getAttribute("popup").equals("true"))
            throw new AssertionError("Neri tools must share one submenu");
        var actions = descriptor.getElementsByTagName("action");
        for (int i = 0; i < actions.getLength(); i++) {
            if (actions.item(i).getParentNode() != menu)
                throw new AssertionError("Neri actions must belong to the Neri submenu");
        }
        for (int i = 0; i < registrations.getLength(); i++) {
            var registration = (org.w3c.dom.Element) registrations.item(i);
            if (!registration.getAttribute("name").equals("Neri")) continue;
            var type = NeriFileType.INSTANCE;
            String expected = type.isSecondary() ? null : type.getLanguage().getID();
            String actual = registration.hasAttribute("language") ? registration.getAttribute("language") : null;
            if (!Objects.equals(expected, actual)) {
                throw new AssertionError("Incorrect language in <fileType>: expected " + expected + ", actual " + actual);
            }
            System.out.println("Neri file type registration matches the Rider SDK contract.");
            return;
        }
        throw new AssertionError("Neri file type registration is missing");
    }
}
