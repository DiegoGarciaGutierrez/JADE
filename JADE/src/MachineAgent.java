import jade.core.Agent;
import java.io.*;
import java.net.*;
import jade.core.behaviours.*;
import jade.lang.acl.ACLMessage;
import jade.lang.acl.MessageTemplate;
import jade.domain.DFService;
import jade.domain.FIPAException;
import jade.domain.FIPAAgentManagement.DFAgentDescription;
import jade.domain.FIPAAgentManagement.ServiceDescription;


import java.util.*;

public class MachineAgent extends Agent {
    String carga;
    String pieza;
    boolean itxaron=false;
    private Hashtable catalogue;


    protected void setup() {
        //katalogoa sortu
        catalogue = new Hashtable();


        DFAgentDescription dfd = new DFAgentDescription();
        dfd.setName(getAID());
        //Template-a prestatu
        ServiceDescription sd = new ServiceDescription();
        sd.setType("makina aukeratu");
        sd.setName(getLocalName());
        dfd.addServices(sd);
        try {
            DFService.register(this, dfd);
        }
        catch (FIPAException fe) {
            fe.printStackTrace();
        }

        addBehaviour(new KargaBalioaBidali());

        addBehaviour(new PiezaOnartu());
        //20 segundo oro makinarekin konektatzen saiatu
        addBehaviour(new TickerBehaviour(this, 20000){
            protected void onTick() {
                //Karga balio zaharra ezabatu

                System.out.println("Makinarekin konexioa ezartzen...");
                try {

                    Socket s = new Socket("localhost", 5555);
                    DataOutputStream dout = new DataOutputStream(s.getOutputStream());
                    BufferedReader din = new BufferedReader(new InputStreamReader(s.getInputStream()));
                    //Informazioa makinatik jaso
                    if (itxaron == false) {
                        dout.writeBytes("ask");
                        dout.flush();
                        carga = din.readLine();
                        System.out.println("Lan karga datua lortzen...");
                        System.out.println(carga);
                        pieza = din.readLine();
                        System.out.println("Pieza mota datua lortzen...");
                        System.out.println(pieza);
                            catalogue.put(pieza, new Integer(carga));

                    }
                    //Makina pieza jasotzeko prestaztea esan
                    if (itxaron == true) {
                        dout.writeBytes("switch");
                        dout.flush();
                        System.out.println("Makina pieza jasotzeko prestatzen...");
                        itxaron=false;
                    }
                        s.close();
                        dout.flush();

                    } catch(Exception e){

                        System.out.println("Konexioa ezin izan da lortu");
                    }

                System.out.println("Makinarekin konexioa mozten");


            }
            });



    }



    protected void takeDown() {
        try {
            DFService.deregister(this);
        }
        catch (FIPAException fe) {
            fe.printStackTrace();
        }

        System.out.println("Makina agente "+getAID().getName()+" bukatzen");
    }


    private class KargaBalioaBidali extends CyclicBehaviour {
        public void action() {
            MessageTemplate mt = MessageTemplate.MatchPerformative(ACLMessage.CFP);
            ACLMessage msg = myAgent.receive(mt);
            if (msg != null) {
                //CFP mezua jaso da
                String dpieza = msg.getContent();
                ACLMessage reply = msg.createReply();
                //Bilatu kargaren balioa katalogoan
                Integer dkarga = (Integer) catalogue.get(dpieza);
                System.out.println("Dkarga "+dkarga);
                if (dkarga != null) {
                    //Proposamena bidali
                    reply.setPerformative(ACLMessage.PROPOSE);
                    reply.setContent(String.valueOf(dkarga.intValue()));
                    System.out.println("Dkarga bidali da");
                }
                else {

                    reply.setPerformative(ACLMessage.REFUSE);
                    reply.setContent("pieza hau ez zait balio");
                }
                myAgent.send(reply);
            }
            else {
                block();
            }
        }
    }


    private class PiezaOnartu extends CyclicBehaviour {
        public void action() {
            MessageTemplate mt = MessageTemplate.MatchPerformative(ACLMessage.ACCEPT_PROPOSAL);
            ACLMessage msg = myAgent.receive(mt);
            if (msg != null) {
                //PROPOSAL mezua jaso da
                String dpieza = msg.getContent();
                ACLMessage reply = msg.createReply();
                Integer dkarga = (Integer) catalogue.get(dpieza);
                //Bilatu kargaren balioa katalogoan
                if (dkarga != null) {
                    reply.setPerformative(ACLMessage.INFORM);
                    System.out.println(dpieza+" onartua "+msg.getSender().getName());
                    itxaron=true;
                    catalogue.remove(pieza);
                }
                else {

                    reply.setPerformative(ACLMessage.FAILURE);
                    reply.setContent("arazoren bat gertatu da");
                }
                myAgent.send(reply);
            }
            else {
                block();
            }
        }
    }
}