using System.Drawing;
using System.IO;

namespace WebPanel;

internal sealed class TrayIconManager : IDisposable
{
    private readonly System.Windows.Forms.NotifyIcon _trayIcon;
    private readonly MainWindow _window;
    private readonly ConfigManager _config;
    private bool _disposed;

    public TrayIconManager(MainWindow window, ConfigManager config)
    {
        _window = window;
        _config = config;

        _trayIcon = new System.Windows.Forms.NotifyIcon
        {
            Text = "WebPanel",
            Visible = true,
            Icon = LoadTrayIcon()
        };

        var menu = new System.Windows.Forms.ContextMenuStrip();
        menu.Items.Add("显示窗口", null, (_, _) => _window.RestoreFromTray());
        menu.Items.Add("刷新页面", null, (_, _) => _window.RefreshPage());
        menu.Items.Add(new System.Windows.Forms.ToolStripSeparator());

        var opacityMenu = new System.Windows.Forms.ToolStripMenuItem("透明度");
        opacityMenu.DropDownItems.Add("100%", null, (_, _) => SetOpacity(1.0));
        opacityMenu.DropDownItems.Add("85%", null, (_, _) => SetOpacity(0.85));
        opacityMenu.DropDownItems.Add("70%", null, (_, _) => SetOpacity(0.70));
        opacityMenu.DropDownItems.Add("50%", null, (_, _) => SetOpacity(0.50));
        menu.Items.Add(opacityMenu);

        var topmostItem = new System.Windows.Forms.ToolStripMenuItem("置顶");
        topmostItem.Checked = _config.Topmost;
        topmostItem.Click += (_, _) =>
        {
            _config.Topmost = !_config.Topmost;
            WindowHelper.SetTopmost(_window, _config.Topmost);
            topmostItem.Checked = _config.Topmost;
            _config.Save();
        };
        menu.Items.Add(topmostItem);

        menu.Items.Add(new System.Windows.Forms.ToolStripSeparator());
        menu.Items.Add("退出", null, (_, _) =>
        {
            _config.Save();
            _trayIcon.Visible = false;
            System.Windows.Application.Current.Shutdown();
        });

        _trayIcon.ContextMenuStrip = menu;
        _trayIcon.DoubleClick += (_, _) => _window.RestoreFromTray();
    }

    private void SetOpacity(double opacity)
    {
        _config.Opacity = opacity;
        _window.SetWindowOpacity(opacity);
        _config.Save();
    }

    private static Icon LoadTrayIcon()
    {
        string icoPath = Path.Combine(AppDomain.CurrentDomain.BaseDirectory, "Assets", "icon.ico");
        if (File.Exists(icoPath))
        {
            return new Icon(icoPath);
        }

        var bitmap = new Bitmap(32, 32);
        using var g = Graphics.FromImage(bitmap);
        g.Clear(Color.FromArgb(0, 120, 215));
        g.DrawString("W", new Font("Arial", 16, FontStyle.Bold), Brushes.White, 4, 2);
        return Icon.FromHandle(bitmap.GetHicon());
    }

    public void Dispose()
    {
        if (_disposed) return;
        _disposed = true;
        _trayIcon.Visible = false;
        _trayIcon.Dispose();
    }
}
