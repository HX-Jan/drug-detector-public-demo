package org.example.detector.desktop;

import java.awt.*;
import java.awt.geom.*;
import javax.swing.*;
import javax.swing.border.*;

/** Mirrors the white, blue and compact portrait layout visible on the reference device. */
final class ReferenceTheme {
  static final Color BLUE = new Color(0x1764ed),
      INK = new Color(0x202c40),
      MUTED = new Color(0x8793a6),
      LINE = new Color(0xe5eaf2),
      PALE = new Color(0xf5f8fc),
      GREEN = new Color(0x28ad79);

  static Font font(int size, boolean bold) {
    return new Font("Microsoft YaHei", bold ? Font.BOLD : Font.PLAIN, size);
  }

  static JPanel column() {
    JPanel p = new JPanel();
    p.setOpaque(false);
    p.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS));
    return p;
  }

  static JPanel scrollColumn() {
    class ViewportColumn extends JPanel implements Scrollable {
      ViewportColumn() {
        setOpaque(false);
        setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
      }

      public Dimension getPreferredScrollableViewportSize() {
        return getPreferredSize();
      }

      public int getScrollableUnitIncrement(Rectangle r, int orientation, int direction) {
        return 16;
      }

      public int getScrollableBlockIncrement(Rectangle r, int orientation, int direction) {
        return Math.max(16, r.height - 32);
      }

      public boolean getScrollableTracksViewportWidth() {
        return true;
      }

      public boolean getScrollableTracksViewportHeight() {
        return false;
      }
    }
    return new ViewportColumn();
  }

  static JPanel panel() {
    JPanel p =
        new JPanel() {
          @Override
          protected void paintComponent(Graphics graphics) {
            super.paintComponent(graphics);
            Graphics2D g = (Graphics2D) graphics.create();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setColor(getBackground());
            g.fillRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 12, 12);
            g.setColor(LINE);
            g.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 12, 12);
            g.dispose();
          }
        };
    p.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS));
    p.setOpaque(false);
    p.setBackground(Color.WHITE);
    p.setBorder(new EmptyBorder(14, 14, 14, 14));
    return p;
  }

  static JLabel label(String value, int size, Color color) {
    JLabel label = new JLabel("<html>" + escape(value).replace("\n", "<br>") + "</html>");
    label.setFont(font(size, false));
    label.setForeground(color);
    label.setAlignmentX(Component.LEFT_ALIGNMENT);
    return label;
  }

  static String escape(String value) {
    return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
  }

  static void add(JPanel p, JComponent c, int height, int gap) {
    c.setAlignmentX(Component.LEFT_ALIGNMENT);
    if (height > 0) {
      c.setPreferredSize(new Dimension(360, height));
      c.setMaximumSize(new Dimension(Integer.MAX_VALUE, height));
    }
    p.add(c);
    if (gap > 0) p.add(Box.createVerticalStrut(gap));
  }

  static JButton button(String text, boolean primary, Runnable action) {
    JButton b =
        new JButton(text) {
          @Override
          protected void paintComponent(Graphics graphics) {
            Graphics2D g = (Graphics2D) graphics.create();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setColor(
                !isEnabled()
                    ? LINE
                    : primary ? BLUE : getModel().isRollover() ? PALE : Color.WHITE);
            g.fillRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 10, 10);
            if (!primary && !getText().equals("‹")) {
              g.setColor(LINE);
              g.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 10, 10);
            }
            g.dispose();
            super.paintComponent(graphics);
          }
        };
    b.setFont(font(13, primary));
    b.setForeground(primary ? Color.WHITE : BLUE);
    b.setFocusPainted(true);
    b.setContentAreaFilled(false);
    b.setBorder(new EmptyBorder(0, 10, 0, 10));
    b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
    b.setAlignmentX(Component.LEFT_ALIGNMENT);
    b.addActionListener(e -> action.run());
    return b;
  }

  static JTextField field(String value) {
    JTextField t = new JTextField(value);
    t.setFont(font(12, false));
    t.setForeground(INK);
    t.setBackground(PALE);
    t.setBorder(new CompoundBorder(new LineBorder(LINE, 1, true), new EmptyBorder(7, 10, 7, 10)));
    return t;
  }

  static final class LineIcon implements Icon {
    private final String name;
    private final int size;
    private final Color color;

    LineIcon(String name, int size, Color color) {
      this.name = name;
      this.size = size;
      this.color = color;
    }

    public int getIconWidth() {
      return size;
    }

    public int getIconHeight() {
      return size;
    }

    public void paintIcon(Component c, Graphics graphics, int x, int y) {
      Graphics2D g = (Graphics2D) graphics.create();
      g.translate(x, y);
      g.scale(size / 32.0, size / 32.0);
      g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
      g.setColor(color);
      g.setStroke(new BasicStroke(1.7f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
      if (name.equals("records")) {
        g.drawRoundRect(7, 4, 18, 24, 3, 3);
        for (int yy = 11; yy <= 23; yy += 6) g.drawLine(12, yy, 21, yy);
      } else if (name.equals("settings")) {
        g.drawOval(7, 7, 18, 18);
        g.drawOval(13, 13, 6, 6);
        for (int i = 0; i < 8; i++) {
          double a = i * Math.PI / 4;
          g.draw(
              new Line2D.Double(
                  16 + Math.cos(a) * 9,
                  16 + Math.sin(a) * 9,
                  16 + Math.cos(a) * 13,
                  16 + Math.sin(a) * 13));
        }
      } else if (name.equals("bluetooth")) {
        Path2D p = new Path2D.Double();
        p.moveTo(16, 3);
        p.lineTo(23, 10);
        p.lineTo(10, 23);
        p.moveTo(10, 9);
        p.lineTo(23, 22);
        p.lineTo(16, 29);
        p.closePath();
        g.draw(p);
      } else if (name.equals("clock")) {
        g.drawOval(4, 4, 24, 24);
        g.drawLine(16, 8, 16, 16);
        g.drawLine(16, 16, 22, 19);
      } else if (name.equals("drop")) {
        Path2D p = new Path2D.Double();
        p.moveTo(16, 3);
        p.curveTo(12, 10, 7, 15, 7, 21);
        p.curveTo(7, 32, 25, 32, 25, 21);
        p.curveTo(25, 15, 20, 10, 16, 3);
        g.draw(p);
      } else {
        g.drawRoundRect(8, 3, 16, 25, 3, 3);
        g.drawRect(11, 7, 10, 12);
        g.drawOval(15, 23, 2, 2);
      }
      g.dispose();
    }
  }

  static final class LoadingRing extends JComponent {
    @Override
    protected void paintComponent(Graphics graphics) {
      Graphics2D g = (Graphics2D) graphics.create();
      g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
      int phase = (int) (System.nanoTime() / 80_000_000L % 12),
          cx = getWidth() / 2,
          cy = getHeight() / 2;
      for (int i = 0; i < 12; i++) {
        double angle = i * Math.PI / 6;
        g.setColor(new Color(23, 100, 237, 45 + Math.floorMod(i - phase, 12) * 18));
        g.fill(
            new Ellipse2D.Double(
                cx + Math.cos(angle) * 12 - 2, cy + Math.sin(angle) * 12 - 2, 4, 4));
      }
      g.dispose();
    }
  }

  static final class Steps extends JComponent {
    private final int current;

    Steps(int current) {
      this.current = current;
      setPreferredSize(new Dimension(360, 65));
      setMaximumSize(new Dimension(Integer.MAX_VALUE, 65));
    }

    @Override
    protected void paintComponent(Graphics graphics) {
      Graphics2D g = (Graphics2D) graphics.create();
      g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
      String[] labels = {"活化", "加样", "反应", "冲洗", "检测"};
      double spacing = (getWidth() - 32) / 4.0;
      for (int i = 0; i < 5; i++) {
        int x = 16 + (int) (i * spacing);
        if (i < 4) {
          g.setColor(i < current ? GREEN : LINE);
          g.drawLine(x + 12, 19, (int) (x + spacing) - 12, 19);
        }
        g.setColor(i < current ? GREEN : i == current ? BLUE : PALE);
        g.fillRoundRect(x - 9, 10, 18, 18, 4, 4);
        g.setColor(i <= current ? Color.WHITE : MUTED);
        g.setFont(font(10, true));
        if (i < current) {
          g.setStroke(new BasicStroke(1.5f));
          g.drawLine(x - 4, 19, x - 1, 22);
          g.drawLine(x - 1, 22, x + 5, 15);
        } else {
          String n = String.valueOf(i + 1);
          g.drawString(n, x - g.getFontMetrics().stringWidth(n) / 2, 23);
        }
        g.setFont(font(10, false));
        g.setColor(i == current ? BLUE : MUTED);
        g.drawString(labels[i], x - g.getFontMetrics().stringWidth(labels[i]) / 2, 49);
      }
      g.dispose();
    }
  }
}
