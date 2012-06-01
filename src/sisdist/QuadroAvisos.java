
package sisdist;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.InetAddress;
import java.net.MalformedURLException;
import java.net.MulticastSocket;
import java.rmi.AlreadyBoundException;
import java.rmi.Naming;
import java.rmi.NotBoundException;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.server.UnicastRemoteObject;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Iterator;

/**
 *
 * @author Vitor, Davi
 */
public class QuadroAvisos extends UnicastRemoteObject implements IQuadroAvisos {
    
    private static QuadroGUI janela;
    
    private MulticastSocket socket;
    private String mcastGroup;
    private int mcastPort;
    private String peerName;
    
    private Listener mcastListener;
    
    private ArrayList<IQuadroAvisos> quadros;
    
    
    private static final String RMI_HOST = "rmi://localhost:1099/";
    private static final int BUFFER_SIZE = 1024;
    
    public QuadroAvisos() throws RemoteException {
        quadros = new ArrayList<>(100);
    }
    
    @Override
    public void setQuadro(IQuadroAvisos quadro) throws RemoteException {
        quadros.add(quadro);
        System.out.println();
        System.out.println("Novo quadro cadastrado.");
        System.out.println("Quadros cadastrados: " + quadros.size());
        System.out.println("---------------------");
    }

    @Override
    public void setAviso(String aviso) throws RemoteException {
        Iterator<IQuadroAvisos> it = quadros.iterator();
        while (it.hasNext()) {
            IQuadroAvisos quadro = it.next();
            try {
                quadro.notificar(aviso);
            } catch (RemoteException ex) {
                it.remove();
                System.out.println();
                System.out.println("Um quadro nao foi encontrado. Removendo referencia...");
                System.out.println("Quadros cadastrados: " + quadros.size());
                System.out.println("---------------------");
            }
        }
    }

    @Override
    public void notificar(String aviso) throws RemoteException {
        janela.mostrarAviso(aviso);
    }
    
    public void registrar(String grupo, int porta) throws IOException {
        this.mcastGroup = grupo;
        this.mcastPort = porta;
        this.peerName = janela.getNome();
        // Se o usuário não pôs nome, é anônimo
        if(peerName.equals("")) {
            peerName = "Anonimo";
        }
        // Retira espaços do nome, gera um timestamp e concatena com o nome para formar uma ID única
        this.peerName = peerName.replaceAll("\\s","");
        this.peerName = peerName.concat(new SimpleDateFormat("HHmmss").format(new java.util.Date()));
        // Tenta criar um RMI Registry. Se já existe, ignora.
        try {
            LocateRegistry.createRegistry(1099);
            System.out.println("Registry criado.");
        } catch (RemoteException ex) {
            System.out.println("Registry já existe.");
        }
        
        // Tenta registrar o quadro no RMI
        try {
            Naming.bind(RMI_HOST + peerName, this);
        } catch (AlreadyBoundException ex) {
            System.out.println("Already Bound Exception: " + ex.getMessage());
        } catch (MalformedURLException ex) {
            System.out.println("Malformed URL Exception: " + ex.getMessage());
        } catch (RemoteException ex) {
            System.out.println("Remote Exception: " + ex.getMessage());
        }
        
        // Entra no grupo multicast
        socket = new MulticastSocket(porta);
        socket.joinGroup(InetAddress.getByName(mcastGroup));
        
        // Avisa para o grupo multicast o nome do novo peer registrado
        enviarPacote(peerName);
        
        // Começa a "ouvir" as mensagens enviadas para o grupo multicast
        mcastListener = new Listener();
        mcastListener.start();
    }
    
    public void desregistrar() throws IOException {
        try {
            // Desregistra no RMI
            Naming.unbind(peerName);
            QuadroAvisos.unexportObject(this, true);
        } catch (RemoteException ex) {
            System.out.println("Remote Exception: " + ex.getMessage());
        } catch (NotBoundException ex) {
            System.out.println("Not Bound Exception: " + ex.getMessage());
        } catch (MalformedURLException ex) {
            System.out.println("Malformed URL Exception: " + ex.getMessage());
        }
        // Sai do grupo multicast e fecha o socket
        socket.leaveGroup(InetAddress.getByName(this.mcastGroup));
        socket.close();
        
        // Para a thread que "ouve" as mensagens do grupo multicast
        mcastListener.stopListener();
    }
    
    public void enviarPacote(String mensagem) throws IOException {
        byte[] buffer = mensagem.getBytes();
        DatagramPacket pacote = new DatagramPacket(buffer, buffer.length, 
                InetAddress.getByName(this.mcastGroup), this.mcastPort);  
        // Envia o pacote para o grupo multicast
        socket.send(pacote);
    }
    
    public void receberPacote(DatagramPacket pacote) {
        try {
            String nome = new String(pacote.getData(), 0, pacote.getLength());
            System.out.println("Recebeu nome \"" + nome + "\".");
            // Procura no RMI pela referencia do quadro com o nome recebido
            IQuadroAvisos quadro = (IQuadroAvisos) Naming.lookup(RMI_HOST + nome);
            // Se o quadro já existe, não adiciona
            for(IQuadroAvisos q : quadros) {
                if(q.equals(quadro)) {
                    System.out.println("Quadro \"" + nome + "\" já cadastrado.");
                    return;
                }
            }
            // Evita que um quadro adicione ele mesmo
            if(!nome.equals(peerName)) {
                quadros.add(quadro);
            }
            // Se adiciona no quadro remoto
            quadro.setQuadro(this);
        } catch (NotBoundException ex) {
            System.out.println("Not Bound Exception: " + ex.getMessage());
        } catch (MalformedURLException ex) {
            System.out.println("Malformed URL Exception: " + ex.getMessage());
        } catch (RemoteException ex) {
            System.out.println("Remote Exception: " + ex.getMessage());
        }
        
    }
    
    public class Listener extends Thread {
        
        private boolean running;
        
        public void stopListener() {
            running = false;
        }
        
        @Override
        public void run() {
            running = true;
            while(running) {
                try {
                    byte[] buffer = new byte[BUFFER_SIZE];
                    DatagramPacket pacote = new DatagramPacket(buffer, buffer.length);
                    socket.receive(pacote);
                    // Pacote recebido, processa a mensagem
                    receberPacote(pacote);
                } catch (IOException ex) {
                    System.out.println("Erro ao receber pacote no listener: " + ex.getMessage());
                }
            }
        }
        
    }
    
    public static void main(String[] args) throws RemoteException {
        QuadroAvisos quadro = new QuadroAvisos();
        janela = new QuadroGUI((QuadroAvisos) quadro);
        janela.setVisible(true);
    }
}
