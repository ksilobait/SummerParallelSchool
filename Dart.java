package ru.nsu.fit;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Map;
import java.util.Scanner;

/**
 * Created by Станислав on 02.07.2018.
 */
public class Dart {
    static InetAddress myIpAdress;
    private static Map<MyInetStruct, Socket> hosts;

    private static class MyInetStruct {
        MyInetStruct(InetAddress ip, int port) {
            this.ip = ip;
            this.port = port;
        }

        InetAddress ip;
        int port;
    }


    private static void print_hosts() {
        for (int i = 0; i < hosts.size(); i++) {
            System.out.print(hosts.get(i) + " ");
        }
        System.out.println();

    }


    private static void conn_net(InetAddress ip, int port) {
        //TODO
    }

    private static void request_hosts(InetAddress ip, int port) throws IOException {
        Socket destination = hosts.get(new MyInetStruct(ip, port));
        PrintWriter out = new PrintWriter(destination.getOutputStream());
        
    }

    private static void connect(InetAddress ip, int port) throws IOException {
        hosts.put(new MyInetStruct(ip, port), new Socket(ip, port));
    }

    private static void myExit() {
        //TODO
    }

    private static void myHelp() {
        //TODO
        System.out.println("LOL");
    }


    public static void main(String[] args) throws IOException {
        System.out.println("Please enter your IP and port:");
        Scanner scanner = new Scanner(System.in);
        String serverIP = scanner.nextLine();
        int serverPort = scanner.nextInt();

        try (Socket socket = new Socket(serverIP, serverPort)) {
            BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            PrintWriter out = new PrintWriter(socket.getOutputStream());
            while (true) {
                String input = in.readLine();
                String[] parsedInput = input.split(" ");

                switch (parsedInput[0]) {
                    case "connect": {
                        //TODO: parse several commands
                        InetAddress ip = InetAddress.getByName(parsedInput[1]);
                        int port = Integer.valueOf(parsedInput[2]);
                        connect(ip, port);
                        break;
                    }
                    case "conn_net": {
                        //TODO
                        InetAddress ip = InetAddress.getByName(parsedInput[1]);
                        int port = Integer.valueOf(parsedInput[2]);
                        conn_net(ip, port);
                        break;
                    }
                    case "request_hosts": {
                        InetAddress ip = InetAddress.getByName(parsedInput[1]);
                        int port = Integer.valueOf(parsedInput[2]);
                        request_hosts(ip, port);
                        break;
                    }
                    case "print_hosts":
                        print_hosts();
                        break;
                    case "exit":
                        myExit();
                        break;
                    case "help":
                        myHelp();
                        break;
                    default:
                        System.out.println("Illegal command!");
                        break;
                }
            }
        } catch (IOException e) {
            System.out.println(e.getMessage());
        }
    }
}
