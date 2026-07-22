import java.io.*;
import java.net.*;

public class Client {
  static String ip;
  static String protocol;
  static String port;

  public static void main(String[]args){
    
    System.out.println("\nLaboratorio #1 - Uso de Sockets");
    
    switch (args.length) {
      case 0:
        System.out.println("No se han ingresado argumentos");
        break;
      default:
          if(args[1].contains("--help")){
            help();
          }else{
            for(int i = 0; i < args.length; i++){
              if(args[i].equals("PORT")){
                System.out.println("Puerto: " + args[i+1]);
                port = args[i+1];
              }
              if(args[i].equals("PROTOCOL")){
                System.out.println("Protocolo: " + args[i+1]);
                protocol = args[i+1];
              }
              if(args[i].equals("IP")){
                System.out.println("IP: " + args[i+1]);
                ip = args[i+1];
              }
            }
          }
   }

   System.out.println(port + " " + protocol + " " + ip);

 }

 static void help(){
  System.out.println("\nUso: java Client PORT <puerto> PROTOCOL <protocolo> IP <ip>\n");
 }

 static void config(String port, String protocol, String ip){
  
 }



}
