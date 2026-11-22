using System;
using System.Collections.Generic;
using System.Drawing;
using System.IO;
using System.Net;
using System.Net.NetworkInformation;
using System.Net.Sockets;
using System.Runtime.InteropServices;
using System.Security.Cryptography;
using System.Text;
using System.Threading;
using System.Windows.Forms;

namespace KeyConnectQuick
{
    static class Win32Input
    {
        public const int INPUT_KEYBOARD = 1;
        public const uint KEYEVENTF_EXTENDEDKEY = 0x0001;
        public const uint KEYEVENTF_KEYUP = 0x0002;
        public const uint KEYEVENTF_UNICODE = 0x0004;
        public const uint MAPVK_VK_TO_VSC = 0;

        [StructLayout(LayoutKind.Sequential)]
        public struct INPUT
        {
            public int type;
            public InputUnion U;
        }

        [StructLayout(LayoutKind.Explicit)]
        public struct InputUnion
        {
            [FieldOffset(0)]
            public MOUSEINPUT mi;
            [FieldOffset(0)]
            public KEYBDINPUT ki;
        }

        [StructLayout(LayoutKind.Sequential)]
        public struct MOUSEINPUT
        {
            public int dx;
            public int dy;
            public uint mouseData;
            public uint dwFlags;
            public uint time;
            public IntPtr dwExtraInfo;
        }

        [StructLayout(LayoutKind.Sequential)]
        public struct KEYBDINPUT
        {
            public ushort wVk;
            public ushort wScan;
            public uint dwFlags;
            public uint time;
            public IntPtr dwExtraInfo;
        }

        [DllImport("user32.dll", SetLastError = true)]
        public static extern uint SendInput(uint nInputs, INPUT[] pInputs, int cbSize);

        [DllImport("user32.dll")]
        public static extern uint MapVirtualKeyW(uint uCode, uint uMapType);

        public static void SendUnicodeText(string text)
        {
            if (string.IsNullOrEmpty(text)) return;
            List<INPUT> list = new List<INPUT>(text.Length * 2);
            for (int i = 0; i < text.Length; i++)
            {
                char ch = text[i];
                if (ch == '\r') continue;
                if (ch == '\n')
                {
                    AppendVkPress(list, 0x0D);
                }
                else if (ch == '\t')
                {
                    AppendVkPress(list, 0x09);
                }
                else
                {
                    ushort unit = (ushort)ch;
                    list.Add(MakeUnicode(unit, false));
                    list.Add(MakeUnicode(unit, true));
                }
            }
            if (list.Count > 0)
            {
                INPUT[] arr = list.ToArray();
                SendInput((uint)arr.Length, arr, Marshal.SizeOf(typeof(INPUT)));
            }
        }

        public static void SendKeyCombo(bool ctrl, bool alt, bool shift, bool win, string keyName)
        {
            ushort vk = MapCanonicalKey(keyName);
            if (vk == 0) return;

            List<ushort> mods = new List<ushort>();
            if (ctrl) mods.Add(0xA2);
            if (alt) mods.Add(0xA4);
            if (shift) mods.Add(0xA0);
            if (win) mods.Add(0x5B);

            List<INPUT> list = new List<INPUT>();
            for (int i = 0; i < mods.Count; i++) list.Add(MakeVk(mods[i], false));
            list.Add(MakeVk(vk, false));
            list.Add(MakeVk(vk, true));
            for (int i = mods.Count - 1; i >= 0; i--) list.Add(MakeVk(mods[i], true));

            INPUT[] arr = list.ToArray();
            SendInput((uint)arr.Length, arr, Marshal.SizeOf(typeof(INPUT)));
        }

        private static void AppendVkPress(List<INPUT> list, ushort vk)
        {
            list.Add(MakeVk(vk, false));
            list.Add(MakeVk(vk, true));
        }

        private static INPUT MakeVk(ushort vk, bool up)
        {
            uint flags = IsExtended(vk) ? KEYEVENTF_EXTENDEDKEY : 0u;
            if (up) flags |= KEYEVENTF_KEYUP;
            ushort scan = (ushort)MapVirtualKeyW(vk, MAPVK_VK_TO_VSC);

            INPUT inp = new INPUT();
            inp.type = INPUT_KEYBOARD;
            inp.U.ki.wVk = vk;
            inp.U.ki.wScan = scan;
            inp.U.ki.dwFlags = flags;
            return inp;
        }

        private static INPUT MakeUnicode(ushort unit, bool up)
        {
            INPUT inp = new INPUT();
            inp.type = INPUT_KEYBOARD;
            inp.U.ki.wVk = 0;
            inp.U.ki.wScan = unit;
            inp.U.ki.dwFlags = up ? (KEYEVENTF_UNICODE | KEYEVENTF_KEYUP) : KEYEVENTF_UNICODE;
            return inp;
        }

        private static bool IsExtended(ushort vk)
        {
            return (vk >= 0x21 && vk <= 0x28) || (vk >= 0x2C && vk <= 0x2E) || vk == 0x5B || vk == 0x5C;
        }

        public static ushort MapCanonicalKey(string key)
        {
            if (string.IsNullOrEmpty(key)) return 0;
            if (key.StartsWith("Key") && key.Length == 4)
            {
                char c = char.ToUpperInvariant(key[3]);
                if (c >= 'A' && c <= 'Z') return (ushort)c;
            }
            if (key.StartsWith("Digit") && key.Length == 6)
            {
                char d = key[5];
                if (d >= '0' && d <= '9') return (ushort)d;
            }
            switch (key)
            {
                case "Backspace": return 0x08;
                case "Tab": return 0x09;
                case "Enter": return 0x0D;
                case "Escape": case "Esc": return 0x1B;
                case "Space": return 0x20;
                case "PageUp": return 0x21;
                case "PageDown": return 0x22;
                case "End": return 0x23;
                case "Home": return 0x24;
                case "ArrowLeft": return 0x25;
                case "ArrowUp": return 0x26;
                case "ArrowRight": return 0x27;
                case "ArrowDown": return 0x28;
                case "PrintScreen": return 0x2C;
                case "Insert": return 0x2D;
                case "Delete": return 0x2E;
                case "F1": return 0x70;
                case "F2": return 0x71;
                case "F3": return 0x72;
                case "F4": return 0x73;
                case "F5": return 0x74;
                case "F6": return 0x75;
                case "F7": return 0x76;
                case "F8": return 0x77;
                case "F9": return 0x78;
                case "F10": return 0x79;
                case "F11": return 0x7A;
                case "F12": return 0x7B;
                case "VolumeMute": return 0xAD;
                case "VolumeDown": return 0xAE;
                case "VolumeUp": return 0xAF;
                case "MediaNextTrack": return 0xB0;
                case "MediaPrevTrack": return 0xB1;
                case "MediaPlayPause": return 0xB3;
                default: return 0;
            }
        }
    }

    public class QuickHostForm : Form
    {
        private const int PORT = 45823;
        private Label statusLabel;
        private TextBox urlsBox;
        private TcpListener listener;
        private volatile bool running = true;
        private string htmlContent;
        private string accessPin;
        private int totalKeys = 0;

        public QuickHostForm()
        {
            Text = "KeyConnect — Windows Instant Keyboard Receiver";
            Size = new Size(620, 390);
            StartPosition = FormStartPosition.CenterScreen;
            BackColor = Color.FromArgb(15, 23, 42);
            ForeColor = Color.White;
            Font = new Font("Segoe UI", 10f);

            Random rng = new Random();
            accessPin = rng.Next(1000, 9999).ToString();

            string exeDir = AppDomain.CurrentDomain.BaseDirectory;
            string htmlPath = Path.Combine(exeDir, "phone-keyboard.html");
            if (File.Exists(htmlPath))
            {
                htmlContent = File.ReadAllText(htmlPath, Encoding.UTF8);
            }
            else
            {
                htmlContent = "<h1>phone-keyboard.html missing</h1>";
            }

            Label title = new Label();
            title.Text = "KeyConnect Ready — افتح الرابط التالي في متصفح هاتفك (Wi-Fi أو سلك USB):";
            title.Font = new Font("Segoe UI", 12f, FontStyle.Bold);
            title.ForeColor = Color.FromArgb(56, 189, 248);
            title.AutoSize = false;
            title.Bounds = new Rectangle(20, 18, 565, 30);
            Controls.Add(title);

            urlsBox = new TextBox();
            urlsBox.Multiline = true;
            urlsBox.ReadOnly = true;
            urlsBox.BackColor = Color.FromArgb(30, 41, 59);
            urlsBox.ForeColor = Color.FromArgb(16, 185, 129);
            urlsBox.Font = new Font("Consolas", 12.5f, FontStyle.Bold);
            urlsBox.BorderStyle = BorderStyle.FixedSingle;
            urlsBox.Bounds = new Rectangle(20, 56, 565, 115);
            urlsBox.Text = BuildUrlsText();
            Controls.Add(urlsBox);

            Label pinInfo = new Label();
            pinInfo.Text = string.Format("رمز الأمان السريع (PIN):  {0}   (مضمّن تلقائياً في الرابط أعلاه)", accessPin);
            pinInfo.ForeColor = Color.FromArgb(226, 232, 240);
            pinInfo.Bounds = new Rectangle(20, 184, 565, 28);
            Controls.Add(pinInfo);

            Label tip = new Label();
            tip.Text = "ملاحظة للاتصال عبر سلك USB بدون Wi-Fi:\r\nفعّل (تقييد USB / USB Tethering) من إعدادات الهاتف وسيظهر رابط USB أعلاه تلقائياً بسرعة < 1ms!";
            tip.ForeColor = Color.FromArgb(148, 163, 184);
            tip.Bounds = new Rectangle(20, 218, 565, 52);
            Controls.Add(tip);

            Button refreshBtn = new Button();
            refreshBtn.Text = "تحديث عناوين الشبكة / USB";
            refreshBtn.BackColor = Color.FromArgb(30, 41, 59);
            refreshBtn.FlatStyle = FlatStyle.Flat;
            refreshBtn.Bounds = new Rectangle(20, 282, 210, 36);
            refreshBtn.Click += delegate { urlsBox.Text = BuildUrlsText(); };
            Controls.Add(refreshBtn);

            statusLabel = new Label();
            statusLabel.Text = "الحالة: بانتظار اتصال الهاتف...";
            statusLabel.ForeColor = Color.FromArgb(56, 189, 248);
            statusLabel.Bounds = new Rectangle(245, 288, 340, 30);
            Controls.Add(statusLabel);

            Thread serverThread = new Thread(ServerLoop);
            serverThread.IsBackground = true;
            serverThread.Start();
        }

        protected override void OnFormClosing(FormClosingEventArgs e)
        {
            running = false;
            try { if (listener != null) listener.Stop(); } catch { }
            base.OnFormClosing(e);
        }

        private string BuildUrlsText()
        {
            StringBuilder sb = new StringBuilder();
            NetworkInterface[] nics = NetworkInterface.GetAllNetworkInterfaces();
            for (int i = 0; i < nics.Length; i++)
            {
                NetworkInterface nic = nics[i];
                if (nic.OperationalStatus != OperationalStatus.Up) continue;
                if (nic.NetworkInterfaceType == NetworkInterfaceType.Loopback) continue;
                IPInterfaceProperties props = nic.GetIPProperties();
                for (int j = 0; j < props.UnicastAddresses.Count; j++)
                {
                    IPAddress ip = props.UnicastAddresses[j].Address;
                    if (ip.AddressFamily == AddressFamily.InterNetwork)
                    {
                        string ipStr = ip.ToString();
                        string tag = (ipStr.StartsWith("192.168.42.") || ipStr.StartsWith("192.168.44."))
                            ? "[USB Cable]" : "[Wi-Fi]";
                        sb.AppendFormat("{0}  http://{1}:{2}/?pin={3}\r\n", tag, ipStr, PORT, accessPin);
                    }
                }
            }
            if (sb.Length == 0)
            {
                sb.AppendFormat("http://127.0.0.1:{0}/?pin={1}\r\n", PORT, accessPin);
            }
            return sb.ToString();
        }

        private void ServerLoop()
        {
            try
            {
                listener = new TcpListener(IPAddress.Any, PORT);
                listener.Start();
                while (running)
                {
                    TcpClient client = listener.AcceptTcpClient();
                    client.NoDelay = true;
                    Thread t = new Thread(delegate() { HandleClient(client); });
                    t.IsBackground = true;
                    t.Start();
                }
            }
            catch { }
        }

        private void HandleClient(TcpClient client)
        {
            try
            {
                using (client)
                using (NetworkStream stream = client.GetStream())
                {
                    string headerText = ReadHttpHeaders(stream);
                    if (string.IsNullOrEmpty(headerText)) return;

                    string[] lines = headerText.Split(new string[] { "\r\n" }, StringSplitOptions.None);
                    string requestLine = lines[0];
                    string wsKey = null;
                    for (int i = 1; i < lines.Length; i++)
                    {
                        if (lines[i].StartsWith("Sec-WebSocket-Key:", StringComparison.OrdinalIgnoreCase))
                        {
                            wsKey = lines[i].Substring(18).Trim();
                        }
                    }

                    if (wsKey != null && requestLine.Contains("/ws"))
                    {
                        if (!requestLine.Contains("pin=" + accessPin))
                        {
                            byte[] denied = Encoding.UTF8.GetBytes("HTTP/1.1 403 Forbidden\r\nContent-Length: 0\r\n\r\n");
                            stream.Write(denied, 0, denied.Length);
                            return;
                        }

                        string acceptKey = ComputeWebSocketAccept(wsKey);
                        string hs = "HTTP/1.1 101 Switching Protocols\r\n" +
                                    "Upgrade: websocket\r\n" +
                                    "Connection: Upgrade\r\n" +
                                    "Sec-WebSocket-Accept: " + acceptKey + "\r\n\r\n";
                        byte[] hsBytes = Encoding.ASCII.GetBytes(hs);
                        stream.Write(hsBytes, 0, hsBytes.Length);

                        UpdateStatus("متصل بالهاتف الآن — اكتب في الهاتف ليظهر في ويندوز!");
                        WebSocketLoop(stream);
                        UpdateStatus("انقطع اتصال الهاتف — بانتظار إعادة الاتصال...");
                    }
                    else
                    {
                        byte[] body = Encoding.UTF8.GetBytes(htmlContent);
                        string hdr = string.Format(
                            "HTTP/1.1 200 OK\r\nContent-Type: text/html; charset=utf-8\r\nContent-Length: {0}\r\nConnection: close\r\n\r\n",
                            body.Length);
                        byte[] hdrBytes = Encoding.ASCII.GetBytes(hdr);
                        stream.Write(hdrBytes, 0, hdrBytes.Length);
                        stream.Write(body, 0, body.Length);
                    }
                }
            }
            catch { }
        }

        private void WebSocketLoop(NetworkStream stream)
        {
            while (running)
            {
                int b0 = stream.ReadByte();
                int b1 = stream.ReadByte();
                if (b0 < 0 || b1 < 0) break;

                int opcode = b0 & 0x0F;
                if (opcode == 8) break; // Close

                bool masked = (b1 & 0x80) != 0;
                long len = b1 & 0x7F;
                if (len == 126)
                {
                    int hi = stream.ReadByte();
                    int lo = stream.ReadByte();
                    len = (hi << 8) | lo;
                }
                else if (len == 127)
                {
                    byte[] lbuf = ReadExact(stream, 8);
                    len = 0;
                    for (int i = 0; i < 8; i++) len = (len << 8) | lbuf[i];
                }

                if (len > 65536) break;
                byte[] mask = masked ? ReadExact(stream, 4) : null;
                byte[] payload = ReadExact(stream, (int)len);
                if (masked)
                {
                    for (int i = 0; i < payload.Length; i++)
                        payload[i] = (byte)(payload[i] ^ mask[i % 4]);
                }

                if (opcode == 1)
                {
                    string msg = Encoding.UTF8.GetString(payload);
                    ProcessCommand(msg);
                }
            }
        }

        private void ProcessCommand(string msg)
        {
            // Lightweight tab-separated framing: "T\t<text>" or "K\t<mods>\t<key>"
            if (msg.StartsWith("T\t"))
            {
                string txt = msg.Substring(2);
                Win32Input.SendUnicodeText(txt);
                Interlocked.Increment(ref totalKeys);
                UpdateStatus(string.Format("متصل ونشط — تم استقبال {0} ضغطة مفتاح", totalKeys));
            }
            else if (msg.StartsWith("K\t"))
            {
                string[] parts = msg.Split('\t');
                if (parts.Length >= 3)
                {
                    string mods = parts[1];
                    string key = parts[2];
                    Win32Input.SendKeyCombo(
                        mods.Contains("C"),
                        mods.Contains("A"),
                        mods.Contains("S"),
                        mods.Contains("M"),
                        key);
                    Interlocked.Increment(ref totalKeys);
                    UpdateStatus(string.Format("متصل ونشط — تم استقبال {0} ضغطة مفتاح", totalKeys));
                }
            }
        }

        private void UpdateStatus(string text)
        {
            if (IsHandleCreated && !IsDisposed)
            {
                try { BeginInvoke((MethodInvoker)delegate { statusLabel.Text = text; }); } catch { }
            }
        }

        private static string ReadHttpHeaders(NetworkStream stream)
        {
            MemoryStream ms = new MemoryStream();
            int matched = 0;
            byte[] seq = new byte[] { 13, 10, 13, 10 };
            while (ms.Length < 8192)
            {
                int b = stream.ReadByte();
                if (b < 0) break;
                ms.WriteByte((byte)b);
                if (b == seq[matched])
                {
                    matched++;
                    if (matched == 4) break;
                }
                else
                {
                    matched = (b == 13) ? 1 : 0;
                }
            }
            return Encoding.ASCII.GetString(ms.ToArray());
        }

        private static byte[] ReadExact(NetworkStream s, int count)
        {
            byte[] buf = new byte[count];
            int off = 0;
            while (off < count)
            {
                int n = s.Read(buf, off, count - off);
                if (n <= 0) throw new EndOfStreamException();
                off += n;
            }
            return buf;
        }

        private static string ComputeWebSocketAccept(string key)
        {
            string combined = key + "258EAFA5-E914-47DA-95CA-C5AB0DC85B11";
            using (SHA1 sha1 = SHA1.Create())
            {
                byte[] hash = sha1.ComputeHash(Encoding.ASCII.GetBytes(combined));
                return Convert.ToBase64String(hash);
            }
        }

        [STAThread]
        public static void Main()
        {
            Application.EnableVisualStyles();
            Application.SetCompatibleTextRenderingDefault(false);
            Application.Run(new QuickHostForm());
        }
    }
}
