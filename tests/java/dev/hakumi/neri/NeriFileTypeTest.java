package dev.hakumi.neri;

import javax.xml.parsers.DocumentBuilderFactory;
import java.nio.file.Path;
import java.util.Objects;

public final class NeriFileTypeTest {
    public static void main(String[] args) throws Exception {
        var descriptor = DocumentBuilderFactory.newInstance().newDocumentBuilder()
            .parse(Path.of(args[0]).toFile());
        var registrations = descriptor.getElementsByTagName("fileType");
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
