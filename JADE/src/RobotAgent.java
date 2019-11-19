import jade.core.Agent;
import jade.core.AID;
import jade.core.behaviours.*;
import jade.lang.acl.ACLMessage;
import jade.lang.acl.MessageTemplate;
import jade.domain.DFService;
import jade.domain.FIPAException;
import jade.domain.FIPAAgentManagement.DFAgentDescription;
import jade.domain.FIPAAgentManagement.ServiceDescription;

public class RobotAgent extends Agent {

    private String pieza;

    private AID[] online;


    protected void setup() {

        System.out.println("Robot agentea "+getAID().getName()+" online");


        Object[] args = getArguments();
        if (args != null && args.length > 0) {
            pieza = (String) args[0];
            System.out.println("Daukadan pieza: "+pieza);

            //Minutu oro pieza mekanizatu ditzaketen makinak bilatuko dira
            addBehaviour(new TickerBehaviour(this, 60000) {
                protected void onTick() {
                    System.out.println("Piezarekin bateragarriak diren makinak bilatzen "+pieza);
                    DFAgentDescription template = new DFAgentDescription();
                    ServiceDescription sd = new ServiceDescription();
                    sd.setType("makina aukeratu");
                    template.addServices(sd);
                    try {
                        //Online dauden makinak zerrendatu
                        DFAgentDescription[] result = DFService.search(myAgent, template);
                        System.out.println("Makinak online:");
                        online = new AID[result.length];
                        for (int i = 0; i < result.length; ++i) {
                            online[i] = result[i].getName();
                            System.out.println(online[i].getName());
                        }
                    }
                    catch (FIPAException fe) {
                        fe.printStackTrace();
                    }

                    myAgent.addBehaviour(new galdetu());
                }
            } );
        }
        else {

            System.out.println("Pieza ez da espezifikatu");
            doDelete();
        }
    }


    protected void takeDown() {


        System.out.println("Robot agentea "+getAID().getName()+" deskonektatzen");
    }


    private class galdetu extends Behaviour {
        private AID makina_hoberena;
        private int kargarik_txikiena=30;
        private int erantzunak = 0;
        private MessageTemplate mt;
        private int step = 0;

        public void action() {
            switch (step) {
                case 0:
                    // Bidali CFP makina guztiei
                    ACLMessage cfp = new ACLMessage(ACLMessage.CFP);
                    for (int i = 0; i < online.length; ++i) {
                        cfp.addReceiver(online[i]);
                    }
                    cfp.setContent(pieza);
                    cfp.setConversationId("negoziazioa");
                    cfp.setReplyWith("cfp"+System.currentTimeMillis());
                    myAgent.send(cfp);
                    // Template-a prestatu proposizioak jasotzeko
                    mt = MessageTemplate.and(MessageTemplate.MatchConversationId("negoziazioa"),
                            MessageTemplate.MatchInReplyTo(cfp.getReplyWith()));
                    System.out.println("cfp bidaltzen");
                    step = 1;
                    break;
                case 1:
                    // Propozisio guztiak jaso makinetatik
                    ACLMessage reply = myAgent.receive(mt);
                    System.out.println("erantzunak jasotzen...");
                    if (reply != null) {
                        System.out.println("erantzunak jaso dira");
                        // Erantzuna jaso da
                        if (reply.getPerformative() == ACLMessage.PROPOSE) {
                            // Proposizioa egingo da
                            int carga = Integer.parseInt(reply.getContent()); //String-etik Int balorera aldatu
                            if (makina_hoberena == null || carga < kargarik_txikiena) {
                                System.out.println("orain arte txikiena");
                                // Makinarik hoberena orain arte
                                kargarik_txikiena = carga;
                                makina_hoberena = reply.getSender();
                            }
                        }
                        erantzunak++;
                        if (erantzunak >= online.length) {
                            System.out.println("erantzun guztiak jaso dira");
                            // Erantzun guztiak jaso dira
                            step = 2;
                        }
                    }
                    else {
                        block();
                    }
                    break;
                case 2:
                    // Pieza bidali ahal den galdetu makinari
                    System.out.println("bidali ahal dizut?");
                    ACLMessage order = new ACLMessage(ACLMessage.ACCEPT_PROPOSAL);
                    order.addReceiver(makina_hoberena);
                    order.setContent(pieza);
                    order.setConversationId("negoziazioa");
                    order.setReplyWith("aukeratua"+System.currentTimeMillis());
                    myAgent.send(order);
                    // Template-a prestatu
                    mt = MessageTemplate.and(MessageTemplate.MatchConversationId("negoziazioa"),
                            MessageTemplate.MatchInReplyTo(order.getReplyWith()));
                    step = 3;
                    break;
                case 3:
                    // Jaso erantzuna eta pieza bidali
                    reply = myAgent.receive(mt);

                    if (reply != null) {
                        System.out.println("guztia ondo irten da");
                        // Ondo jaso da
                        if (reply.getPerformative() == ACLMessage.INFORM) {

                            System.out.println(pieza+" bidali da "+reply.getSender().getName());
                            System.out.println("Makinaren karga pieza jaso aurretik = "+kargarik_txikiena);
                            myAgent.doDelete();
                        }
                        else {
                            System.out.println("Makina beste robot batekin okupatuta dago");
                        }

                        step = 4;
                    }
                    else {
                        block();
                    }
                    break;
            }
        }

        public boolean done() {
            if (step == 2 && makina_hoberena == null) {
                System.out.println("Errorea: "+pieza+" ez du makinarik online");
            }
            return ((step == 2 && makina_hoberena == null) || step == 4);
        }
    }
}
