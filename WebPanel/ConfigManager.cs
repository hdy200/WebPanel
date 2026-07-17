using System.IO;
using System.Runtime.InteropServices;

namespace WebPanel;

internal sealed class ConfigManager
{
    private const string SectionGeneral = "General";
    private const string SectionWindow = "Window";

    private readonly string _configPath;

    public string Url { get; set; } = "https://news.qq.com";
    public int RefreshInterval { get; set; } = 300;
    public double Opacity { get; set; } = 0.85;
    public bool Topmost { get; set; } = true;
    public int ContentCheckInterval { get; set; } = 30;
    public int HideDelayMinutes { get; set; } = 10;

    public ConfigManager(string configPath)
    {
        _configPath = configPath;
    }

    public void Load()
    {
        if (!File.Exists(_configPath))
        {
            CreateDefaultConfig();
        }

        Url = ReadString(SectionGeneral, nameof(Url), Url);
        RefreshInterval = ReadInt(SectionGeneral, nameof(RefreshInterval), RefreshInterval);
        ContentCheckInterval = ReadInt(SectionGeneral, nameof(ContentCheckInterval), ContentCheckInterval);
        HideDelayMinutes = ReadInt(SectionGeneral, nameof(HideDelayMinutes), HideDelayMinutes);
        Opacity = ReadDouble(SectionWindow, nameof(Opacity), Opacity);
        Topmost = ReadBool(SectionWindow, nameof(Topmost), Topmost);
    }

    public void Save()
    {
        WriteString(SectionGeneral, nameof(Url), Url);
        WriteInt(SectionGeneral, nameof(RefreshInterval), RefreshInterval);
        WriteInt(SectionGeneral, nameof(ContentCheckInterval), ContentCheckInterval);
        WriteInt(SectionGeneral, nameof(HideDelayMinutes), HideDelayMinutes);
        WriteDouble(SectionWindow, nameof(Opacity), Opacity);
        WriteBool(SectionWindow, nameof(Topmost), Topmost);
    }

    private void CreateDefaultConfig()
    {
        var dir = Path.GetDirectoryName(_configPath);
        if (!string.IsNullOrEmpty(dir) && !Directory.Exists(dir))
        {
            Directory.CreateDirectory(dir);
        }

        File.WriteAllText(_configPath,
            $"""
            [General]
            ; 网页地址
            Url={Url}
            ; 自动刷新间隔（秒），0 表示不刷新
            RefreshInterval={RefreshInterval}
            ; 内容检测间隔（秒），0 表示不检测
            ContentCheckInterval={ContentCheckInterval}
            ; 无新内容后隐藏窗口延迟（分钟），0 表示不隐藏
            HideDelayMinutes={HideDelayMinutes}

            [Window]
            ; 窗口透明度 0.0~1.0
            Opacity={Opacity.ToString(System.Globalization.CultureInfo.InvariantCulture)}
            ; 是否始终在最前面
            Topmost={(Topmost ? "true" : "false")}

            """);
    }

    #region INI P/Invoke

    [DllImport("kernel32.dll", CharSet = CharSet.Unicode, SetLastError = true)]
    private static extern int GetPrivateProfileString(
        string section, string key, string defaultValue,
        char[] retVal, int size, string filePath);

    [DllImport("kernel32.dll", CharSet = CharSet.Unicode, SetLastError = true)]
    private static extern bool WritePrivateProfileString(
        string section, string key, string value, string filePath);

    private string ReadString(string section, string key, string defaultValue)
    {
        var buffer = new char[4096];
        int len = GetPrivateProfileString(section, key, defaultValue, buffer, buffer.Length, _configPath);
        return new string(buffer, 0, len);
    }

    private int ReadInt(string section, string key, int defaultValue)
    {
        string value = ReadString(section, key, defaultValue.ToString());
        return int.TryParse(value, out int result) ? result : defaultValue;
    }

    private double ReadDouble(string section, string key, double defaultValue)
    {
        string value = ReadString(section, key, defaultValue.ToString(System.Globalization.CultureInfo.InvariantCulture));
        return double.TryParse(value, System.Globalization.NumberStyles.Float,
            System.Globalization.CultureInfo.InvariantCulture, out double result) ? result : defaultValue;
    }

    private bool ReadBool(string section, string key, bool defaultValue)
    {
        string value = ReadString(section, key, defaultValue.ToString());
        return value.Equals("true", StringComparison.OrdinalIgnoreCase);
    }

    private void WriteString(string section, string key, string value)
        => WritePrivateProfileString(section, key, value, _configPath);

    private void WriteInt(string section, string key, int value)
        => WriteString(section, key, value.ToString());

    private void WriteDouble(string section, string key, double value)
        => WriteString(section, key, value.ToString(System.Globalization.CultureInfo.InvariantCulture));

    private void WriteBool(string section, string key, bool value)
        => WriteString(section, key, value ? "true" : "false");

    #endregion
}
