using System.Windows;
using System.Windows.Threading;

namespace WebPanel;

public partial class MainWindow : Window
{
    private readonly ConfigManager _config;
    private readonly DispatcherTimer _refreshTimer;
    private readonly DispatcherTimer _contentCheckTimer;
    private readonly DispatcherTimer _hideTimer;
    private TrayIconManager? _trayManager;
    private string? _lastContentHash;

    private const string ContentCheckScript = """
        (function() {
            var text = document.body ? document.body.innerText : '';
            text = text
                .replace(/\d{4}[\/\-]\d{1,2}[\/\-]\d{1,2}\s+\d{1,2}:\d{2}(:\d{2})?/g, '')
                .replace(/\d{14}/g, '')
                .replace(/\d{1,2}:\d{2}(:\d{2})?/g, '')
                .replace(/\s+/g, ' ')
                .trim();
            var hash = 0;
            for (var i = 0; i < text.length; i++) {
                var ch = text.charCodeAt(i);
                hash = ((hash << 5) - hash) + ch;
                hash |= 0;
            }
            return text.length + ':' + hash;
        })()
        """;

    public MainWindow()
    {
        InitializeComponent();

        var configPath = System.IO.Path.Combine(AppDomain.CurrentDomain.BaseDirectory, "config.ini");
        _config = new ConfigManager(configPath);
        _config.Load();

        _refreshTimer = new DispatcherTimer
        {
            Interval = TimeSpan.FromSeconds(_config.RefreshInterval)
        };
        _refreshTimer.Tick += (_, _) => RefreshPage();

        _contentCheckTimer = new DispatcherTimer
        {
            Interval = TimeSpan.FromSeconds(_config.ContentCheckInterval)
        };
        _contentCheckTimer.Tick += async (_, _) => await CheckContentAsync();

        _hideTimer = new DispatcherTimer
        {
            Interval = TimeSpan.FromMinutes(_config.HideDelayMinutes)
        };
        _hideTimer.Tick += (_, _) =>
        {
            _hideTimer.Stop();
            if (_config.HideDelayMinutes > 0)
            {
                Hide();
            }
        };

        Loaded += MainWindow_Loaded;
    }

    private async void MainWindow_Loaded(object sender, RoutedEventArgs e)
    {
        WindowHelper.RemoveResizeBorders(this);
        WindowHelper.PinToScreenRight(this);
        WindowHelper.ApplyTopmostAndOpacity(this, _config.Topmost, _config.Opacity);
        WindowHelper.WatchWindowPosition(this);

        await WebView.EnsureCoreWebView2Async();
        WebView.CoreWebView2.Navigate(_config.Url);
        WebView.NavigationCompleted += async (_, _) =>
        {
            await Task.Delay(2000);
            await CheckContentAsync();
        };

        if (_config.RefreshInterval > 0)
        {
            _refreshTimer.Start();
        }

        if (_config.ContentCheckInterval > 0 && _config.HideDelayMinutes > 0)
        {
            _contentCheckTimer.Start();
        }

        _trayManager = new TrayIconManager(this, _config);
    }

    private async Task CheckContentAsync()
    {
        if (WebView.CoreWebView2 == null) return;

        try
        {
            string? result = await WebView.CoreWebView2.ExecuteScriptAsync(ContentCheckScript);
            if (result == null) return;

            string currentHash = result.Trim('"');
            if (_lastContentHash == null)
            {
                _lastContentHash = currentHash;
                return;
            }

            if (currentHash != _lastContentHash)
            {
                _lastContentHash = currentHash;
                OnContentChanged();
            }
        }
        catch
        {
        }
    }

    private void OnContentChanged()
    {
        if (!IsVisible)
        {
            Show();
            WindowHelper.SetTopmost(this, _config.Topmost);
        }

        _hideTimer.Stop();
        if (_config.HideDelayMinutes > 0)
        {
            _hideTimer.Start();
        }
    }

    public void RefreshPage()
    {
        if (WebView.CoreWebView2 != null)
        {
            WebView.Reload();
        }
    }

    public void MinimizeToTray()
    {
        _hideTimer.Stop();
        Hide();
    }

    public void RestoreFromTray()
    {
        _hideTimer.Stop();
        Show();
        WindowHelper.SetTopmost(this, _config.Topmost);
        if (_config.HideDelayMinutes > 0)
        {
            _hideTimer.Start();
        }
    }

    public void SetWindowOpacity(double opacity)
    {
        WindowHelper.SetOpacity(this, opacity);
    }

    private void MinimizeButton_Click(object sender, RoutedEventArgs e)
    {
        MinimizeToTray();
    }

    protected override void OnClosed(EventArgs e)
    {
        _refreshTimer.Stop();
        _contentCheckTimer.Stop();
        _hideTimer.Stop();
        _trayManager?.Dispose();
        base.OnClosed(e);
    }
}
