package at.aau.sysseclab.elsantner.digitalwallet;

import javax.smartcardio.*;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class Main {

    private static Card card = null;
    private static CardChannel chnl;
    private static boolean appletSelected = false;
    private static byte CLA = 0x00;

    private static Map<String, String> commandHelp;

    public static void main(String[] args) throws IOException, CardException {
        initCommandHelp();
        System.out.println("Welcome to Digital Wallet Client 1.0");
        BufferedReader in = new BufferedReader(new InputStreamReader(System.in));
        String cmd = "";
        do {
            try {
                TerminalFactory factory = TerminalFactory.getDefault();
                List<CardTerminal> terminals = factory.terminals().list();
                CardTerminal terminal = terminals.get(0);
                card = terminal.connect("T=1");
                chnl = card.getBasicChannel();
                selectApplet();
            } catch (CardException ex) {
                System.out.println(ex.getMessage());
                ex.printStackTrace();
                System.out.println("Error connecting to Smartcard. Press Enter to retry.");
                cmd = in.readLine();
                if (cmd.equals("exit")) {
                    if (card != null)
                        card.disconnect(false);
                    return;
                }
            }
        } while (!appletSelected);

        System.out.println("Please enter a command below or type 'help' for more information");

        do {
            try {
                System.out.print("> ");
                cmd = in.readLine();
                switch (cmd.split(" ")[0]) {
                    case "help":
                        help();
                        break;
                    case "verify":
                        verify(cmd.split(" "));
                        break;
                    case "change":
                        change(cmd.split(" "));
                        break;
                    case "status":
                        status(cmd.split(" "));
                        break;
                    case "age18":
                        age18(cmd.split(" "));
                        break;
                    case "unlock":
                        unlock(cmd.split(" "));
                        break;
                    case "balance":
                        balance(cmd.split(" "));
                        break;
                    case "credit":
                        credit(cmd.split(" "));
                        break;
                    case "debit":
                        debit(cmd.split(" "));
                        break;
                    case "log":
                        log(cmd.split(" "));
                        break;
                    case "read":
                        read(cmd.split(" "));
                        break;
                    case "modify":
                        modify(cmd.split(" "));
                        break;
                    case "exit":
                        break;
                    default:
                        System.out.println("Unknown command. Type 'help' for more information.");
                        break;
                }
            } catch (Exception ex) {
                ex.printStackTrace();
                System.out.println(ex.getMessage());
            }
        } while (!cmd.equals("exit"));
        card.disconnect(false);
    }

    private static void selectApplet() throws CardException {
        byte[] aid = {(byte)0xAB, (byte)0xCD, (byte)0xEF, (byte)0xFE, (byte)0xDC, (byte)0x12, (byte)0x34, (byte)0x56};
        ResponseAPDU r = sendCommand(CLA, (byte)0xA4, (byte)0x04, (byte)0x00, aid);
        if (r.getSW() != 0x9000) {
            throw new CardException("Could not select applet");
        } else {
            appletSelected = true;
        }
    }

    private static void help() {
        System.out.println("The following commands are available: ");
        for (String cmdHelp : commandHelp.values()) {
            System.out.println("  " + cmdHelp);
        }
    }

    private static void initCommandHelp() {
        commandHelp = new HashMap<String, String>();
        commandHelp.put("verify", "verify {pin number 1-3} {pin code}");
        commandHelp.put("change", "change {pin number 1-3} {new pin code}");
        commandHelp.put("status", "status");
        commandHelp.put("age18", "age18 [comparing date in format ddMMyyyy]");
        commandHelp.put("unlock" , "unlock {puk} {new pin code}");
        commandHelp.put("balance" , "balance");
        commandHelp.put("credit", "credit {number with format {65535-0}[.0-99]}");
        commandHelp.put("debit", "debit {number with format {65535-0}[.0-99]}");
        commandHelp.put("log", "log");
        commandHelp.put("read", "read");
        commandHelp.put("modify", "modify {first name} {last name} {birthdate date in format ddMMyyyy}");
    }

    private static void verify(String[] params) throws CardException {
        if (params.length != 3) {
            throw new IllegalArgumentException("Wrong number of Arguments (Format is '" + commandHelp.get("verify") + "')");
        }
        byte pinNumber = parsePinNumber(params[1]);
        ResponseAPDU r = sendCommand(CLA, (byte)0x20, (byte)0x00, pinNumber, params[2].getBytes(StandardCharsets.US_ASCII));
        if (r.getSW() != 0x9000) {
            System.out.println("Error: " + r.getSW());
        }
    }

    private static void change(String[] params) throws CardException {
        if (params.length != 3) {
            throw new IllegalArgumentException("Wrong number of Arguments (Format is '" + commandHelp.get("change") + ")");
        }
        byte pinNumber = parsePinNumber(params[1]);
        ResponseAPDU r = sendCommand(CLA, (byte)0x21, (byte)0x00, pinNumber, params[2].getBytes(StandardCharsets.US_ASCII));
        if (r.getSW() != 0x9000) {
            System.out.println("Error: " + r.getSW());
        }
    }

    private static void status(String[] params) throws CardException {
        if (params.length != 1) {
            throw new IllegalArgumentException("Wrong number of Arguments (Format is '" + commandHelp.get("status") + "')");
        }
        ResponseAPDU r = sendCommand(CLA, (byte)0x22, (byte)0x00, (byte)0x00);
        if (r.getSW() != 0x9000) {
            System.out.println("Error: " + r.getSW());
        } else {
            for (int i=0; i<3; i++)
                System.out.println("PIN" + (i+1) + ": " + ((r.getData()[i] == 0x01) ? "Changed" : "Not Changed"));
        }
    }

    private static void age18(String[] params) throws CardException {
        DateTimeFormatter dtf = DateTimeFormatter.ofPattern("ddMMyyyy");
        String dateStr = LocalDate.now().format(dtf);
        if (params.length > 2) {
            throw new IllegalArgumentException("Wrong number of Arguments (Format is '" + commandHelp.get("age18") + "')");
        } else if (params.length == 2) {
            dtf.parse(params[1]);
            dateStr = params[1];
        }
        ResponseAPDU r = sendCommand(CLA, (byte)0x23, (byte)0x00, (byte)0x00, dateStr.getBytes(StandardCharsets.US_ASCII));
        if (r.getSW() != 0x9000) {
            System.out.println("Error: " + r.getSW());
        } else {
            System.out.println(r.getData()[0] == 0x01 ? "True" : "False");
        }
    }

    private static void unlock(String[] params) throws CardException {
        if (params.length != 3) {
            throw new IllegalArgumentException("Wrong number of Arguments (Format is '" + commandHelp.get("unlock") + "')");
        }
        byte[] data = new byte[13];
        System.arraycopy(data, 0, params[1].getBytes(StandardCharsets.US_ASCII), 0, 8);
        data[8]=(byte)0xFF;
        System.arraycopy(data, 9, params[2].getBytes(StandardCharsets.US_ASCII), 0, 4);

        ResponseAPDU r = sendCommand(CLA, (byte)0x24, (byte)0x00, (byte)0x00, data);
        if (r.getSW() != 0x9000) {
            System.out.println("Error: " + r.getSW());
        }
    }

    private static void balance(String[] params) throws CardException {
        if (params.length != 1) {
            throw new IllegalArgumentException("Wrong number of Arguments (Format is '" + commandHelp.get("balance") + "')");
        }
        ResponseAPDU r = sendCommand(CLA, (byte)0x25, (byte)0x00, (byte)0x00);
        if (r.getSW() != 0x9000) {
            System.out.println("Error: " + r.getSW());
        } else {
            int balance = r.getData()[0] << 24 | r.getData()[1] << 16 | r.getData()[2] << 8 | r.getData()[3];
            System.out.println(balance + "." + r.getData()[4]);
        }
    }

    private static void credit(String[] params) throws CardException {
        if (params.length != 2) {
            throw new IllegalArgumentException("Wrong number of Arguments (Format is '" + commandHelp.get("credit") + "')");
        }

        ResponseAPDU r = sendCommand(CLA, (byte)0x26, (byte)0x00, (byte)0x00, parseCreditValue(params[1]));
        if (r.getSW() != 0x9000) {
            System.out.println("Error: " + r.getSW());
        }
    }

    private static void debit(String[] params) throws CardException {
        if (params.length != 2) {
            throw new IllegalArgumentException("Wrong number of Arguments (Format is '" + commandHelp.get("debit") + "')");
        }

        ResponseAPDU r = sendCommand(CLA, (byte)0x27, (byte)0x00, (byte)0x00, parseCreditValue(params[1]));
        if (r.getSW() != 0x9000) {
            System.out.println("Error: " + r.getSW());
        }
    }

    private static void log(String[] params) throws CardException {
        if (params.length != 1) {
            throw new IllegalArgumentException("Wrong number of Arguments (Format is '" + commandHelp.get("log") + "')");
        }
        ResponseAPDU r = sendCommand(CLA, (byte)0x28, (byte)0x00, (byte)0x00);
        if (r.getSW() != 0x9000) {
            System.out.println("Error: " + r.getSW());
        } else {
            byte[] data = r.getData();
            for (int i=9; i>0; i++)
                if (data[i*4] != 0x00) {
                    System.out.println(data[i*4] == 0x01 ? " + " : " - " +
                            (data[i*4+1] << 8 | data[i*4+2]) + "." + data[i*4+3]);
                }
        }
    }

    private static void read(String[] params) throws CardException {
        if (params.length != 1) {
            throw new IllegalArgumentException("Wrong number of Arguments (Format is '" + commandHelp.get("read") + "')");
        }
        ResponseAPDU r = sendCommand(CLA, (byte)0xB6, (byte)0x00, (byte)0x00);
        if (r.getSW() != 0x9000) {
            System.out.println("Error: " + r.getSW());
        } else {
            byte[] buffer = new byte[30];
            System.arraycopy(buffer, 0, r.getData(), 0, 30);
            System.out.println("First name: " + new String(buffer));
            System.arraycopy(buffer, 0, r.getData(), 30, 30);
            System.out.println("Last name: " + new String(buffer));
            Arrays.fill(buffer, (byte)20);
            System.arraycopy(buffer, 0, r.getData(), 60, 8);
            System.out.println("Birthdate: " + new String(buffer));
        }
    }

    private static void modify(String[] params) throws CardException {
        if (params.length != 4) {
            throw new IllegalArgumentException("Wrong number of Arguments (Format is '" + commandHelp.get("modify") + "')");
        }
        // length of first name + 0xFF + last name + 0xFF + birthdate + 0xFF
        byte[] data = new byte[params[1].length()+params[2].length()+params[3].length()+3];
        System.arraycopy(params[1].getBytes(StandardCharsets.US_ASCII), 0, data, 0, params[1].length());
        data[params[1].length()] = (byte)0xFF;
        System.arraycopy(params[2].getBytes(StandardCharsets.US_ASCII), 0, data, params[1].length()+1, params[2].length());
        data[params[1].length()+1+params[2].length()] = (byte)0xFF;
        System.arraycopy(params[3].getBytes(StandardCharsets.US_ASCII), 0, data, params[1].length()+1+params[2].length()+1, params[3].length());
        data[data.length-1] = (byte)0xFF;

        ResponseAPDU r = sendCommand(CLA, (byte)0xD6, (byte)0x00, (byte)0x00, data);
        if (r.getSW() != 0x9000) {
            System.out.println("Error: " + r.getSW());
        }
    }

    private static byte[] parseCreditValue(String str) {
        float f = Float.parseFloat(str);
        byte[] data = new byte[3];
        data[0] = (byte)(((short)f) >> 8);
        data[1] = (byte)(f);
        String fStr = String.format(java.util.Locale.US,"%.2f", f);
        if (fStr.contains(".")) {
            fStr = fStr.substring(fStr.lastIndexOf("."), fStr.lastIndexOf(".")+2);
            data[2] = Byte.parseByte(fStr);
        }
        return data;
    }

    private static byte parsePinNumber(String pinStr) {
        byte pinNumber;
        try {
            pinNumber = Byte.parseByte(pinStr);
            pinNumber -= 0x31;  // convert from ASCII to byte value
            return pinNumber;
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException("Pin number must be between 1-3");
        }
    }

    private static ResponseAPDU sendCommand(byte cla, byte ins, byte p1, byte p2, byte[] data) throws CardException {
        return chnl.transmit(new CommandAPDU(cla,ins,p1,p2,data));
    }

    private static ResponseAPDU sendCommand(byte cla, byte ins, byte p1, byte p2) throws CardException {
        return chnl.transmit(new CommandAPDU(cla,ins,p1,p2));
    }
}
