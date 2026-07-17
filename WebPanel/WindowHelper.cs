using System.Runtime.InteropServices;
using System.Windows;
using System.Windows.Interop;

namespace WebPanel;

internal static class WindowHelper
{
    private const int GWL_EXSTYLE = -20;
    private const int GWL_STYLE = -16;

    private const int WS_THICKFRAME = 0x00040000;
    private const int WS_SYSMENU = 0x00080000;
    private const int WS_MAXIMIZEBOX = 0x00010000;
    private const int WS_MINIMIZEBOX = 0x00020000;

    private const int WS_EX_LAYERED = 0x00080000;
    private const int WS_EX_TOPMOST = 0x00000008;

    private const int LWA_ALPHA = 0x00000002;

    private const uint SWP_NOMOVE = 0x0002;
    private const uint SWP_NOSIZE = 0x0001;
    private const uint SWP_NOACTIVATE = 0x0010;
    private const uint SWP_SHOWWINDOW = 0x0040;

    private const int DRAG_HANDLE_HEIGHT = 32;

    private static readonly IntPtr HWND_TOPMOST = new(-1);
    private static readonly IntPtr HWND_NOTOPMOST = new(-2);
    private static Window? _managedWindow;

    [DllImport("user32.dll")]
    private static extern int SetWindowLong(IntPtr hWnd, int nIndex, int dwNewLong);

    [DllImport("user32.dll")]
    private static extern int GetWindowLong(IntPtr hWnd, int nIndex);

    [DllImport("user32.dll")]
    private static extern bool SetWindowPos(IntPtr hWnd, IntPtr hWndInsertAfter,
        int x, int y, int cx, int cy, uint uFlags);

    [DllImport("user32.dll")]
    private static extern bool SetLayeredWindowAttributes(IntPtr hwnd, uint crKey, byte bAlpha, uint dwFlags);

    [DllImport("user32.dll")]
    private static extern bool GetCursorPos(out POINT lpPoint);

    [DllImport("user32.dll")]
    private static extern IntPtr WindowFromPoint(POINT point);

    [StructLayout(LayoutKind.Sequential)]
    private struct POINT
    {
        public int X, Y;
    }

    public static void RemoveResizeBorders(Window window)
    {
        var hwnd = new WindowInteropHelper(window).Handle;
        int style = GetWindowLong(hwnd, GWL_STYLE);
        style &= ~(WS_THICKFRAME | WS_SYSMENU | WS_MAXIMIZEBOX | WS_MINIMIZEBOX);
        SetWindowLong(hwnd, GWL_STYLE, style);
    }

    public static void ApplyTopmostAndOpacity(Window window, bool topmost, double opacity)
    {
        var hwnd = new WindowInteropHelper(window).Handle;

        int exStyle = GetWindowLong(hwnd, GWL_EXSTYLE);
        exStyle |= WS_EX_LAYERED;
        if (topmost)
            exStyle |= WS_EX_TOPMOST;
        else
            exStyle &= ~WS_EX_TOPMOST;
        SetWindowLong(hwnd, GWL_EXSTYLE, exStyle);

        byte alpha = (byte)(opacity * 255);
        SetLayeredWindowAttributes(hwnd, 0, alpha, LWA_ALPHA);

        if (topmost)
            SetWindowPos(hwnd, HWND_TOPMOST, 0, 0, 0, 0,
                SWP_NOMOVE | SWP_NOSIZE | SWP_NOACTIVATE | SWP_SHOWWINDOW);
        else
            SetWindowPos(hwnd, HWND_NOTOPMOST, 0, 0, 0, 0,
                SWP_NOMOVE | SWP_NOSIZE | SWP_NOACTIVATE | SWP_SHOWWINDOW);
    }

    public static void SetOpacity(Window window, double opacity)
    {
        var hwnd = new WindowInteropHelper(window).Handle;
        byte alpha = (byte)(opacity * 255);
        SetLayeredWindowAttributes(hwnd, 0, alpha, LWA_ALPHA);
    }

    public static void SetTopmost(Window window, bool topmost)
    {
        var hwnd = new WindowInteropHelper(window).Handle;

        int exStyle = GetWindowLong(hwnd, GWL_EXSTYLE);
        if (topmost)
            exStyle |= WS_EX_TOPMOST;
        else
            exStyle &= ~WS_EX_TOPMOST;
        SetWindowLong(hwnd, GWL_EXSTYLE, exStyle);

        if (topmost)
            SetWindowPos(hwnd, HWND_TOPMOST, 0, 0, 0, 0,
                SWP_NOMOVE | SWP_NOSIZE | SWP_NOACTIVATE | SWP_SHOWWINDOW);
        else
            SetWindowPos(hwnd, HWND_NOTOPMOST, 0, 0, 0, 0,
                SWP_NOMOVE | SWP_NOSIZE | SWP_NOACTIVATE | SWP_SHOWWINDOW);
    }

    public static void PinToScreenRight(Window window)
    {
        double screenWidth = SystemParameters.PrimaryScreenWidth;
        double screenHeight = SystemParameters.PrimaryScreenHeight;
        double oneThird = screenWidth / 3.0;
        double height = screenHeight * 0.8;
        double top = (screenHeight - height) / 2.0;

        int left = (int)(screenWidth - oneThird);

        var hwnd = new WindowInteropHelper(window).Handle;
        SetWindowPos(hwnd, HWND_TOPMOST, left, (int)top, (int)oneThird, (int)height, SWP_SHOWWINDOW);
    }

    public static void WatchWindowPosition(Window window)
    {
        _managedWindow = window;
        var source = HwndSource.FromHwnd(new WindowInteropHelper(window).Handle);
        source?.AddHook(WndProc);
    }

    private static IntPtr WndProc(IntPtr hwnd, int msg, IntPtr wParam, IntPtr lParam, ref bool handled)
    {
        const int WM_NCHITTEST = 0x0084;

        if (msg == WM_NCHITTEST && _managedWindow != null)
        {
            GetCursorPos(out POINT pt);
            int windowTop = (int)_managedWindow.Top;
            int windowRight = (int)(_managedWindow.Left + _managedWindow.Width);

            if (pt.Y >= windowTop && pt.Y < windowTop + DRAG_HANDLE_HEIGHT)
            {
                if (pt.X >= windowRight - 32)
                {
                    const int HTCLIENT = 1;
                    handled = true;
                    return (IntPtr)HTCLIENT;
                }

                const int HTCAPTION = 2;
                handled = true;
                return (IntPtr)HTCAPTION;
            }
        }

        return IntPtr.Zero;
    }
}
