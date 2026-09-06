package dev.hakumi.neri;

import com.intellij.openapi.actionSystem.IdeActions;
import com.intellij.openapi.actionSystem.MouseShortcut;
import com.intellij.openapi.keymap.Keymap;

import javax.swing.JPanel;
import java.awt.event.InputEvent;
import java.awt.event.MouseEvent;
import java.lang.reflect.Proxy;

public final class NeriNavigationSupportTest {
    private static MouseEvent click(int modifiers, int count) {
        return new MouseEvent(new JPanel(), MouseEvent.MOUSE_CLICKED, 0, modifiers, 4, 4, count,
                false, MouseEvent.BUTTON1);
    }

    private static Keymap keymap(MouseShortcut accepted) {
        return (Keymap) Proxy.newProxyInstance(Keymap.class.getClassLoader(), new Class[]{Keymap.class},
                (proxy, method, args) -> method.getName().equals("hasActionId")
                        && IdeActions.ACTION_GOTO_DECLARATION.equals(args[0]) && accepted.equals(args[1]));
    }

    public static void main(String[] args) {
        MouseShortcut controlClick = new MouseShortcut(MouseEvent.BUTTON1, InputEvent.CTRL_DOWN_MASK, 1);
        if (!NeriNavigationSupport.isDeclarationShortcut(keymap(controlClick),
                click(InputEvent.CTRL_DOWN_MASK | InputEvent.BUTTON1_DOWN_MASK, 1))
                || NeriNavigationSupport.isDeclarationShortcut(keymap(controlClick), click(0, 1))
                || NeriNavigationSupport.isDeclarationShortcut(keymap(controlClick),
                click(InputEvent.CTRL_DOWN_MASK, 2))) {
            throw new AssertionError("Declaration navigation must follow the active keymap's single-click shortcut");
        }
        System.out.println("Neri declaration mouse shortcut behavior passed.");
    }
}
