package gui;

import gui.View.LoginPanel;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileReader;
import java.io.FileWriter;
import java.util.Vector;

import javax.swing.AbstractButton;
import javax.swing.JInternalFrame;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.event.InternalFrameEvent;
import javax.swing.event.InternalFrameListener;

import main.Instance;

public class Controller {
	private String accountsFilePath = "Ressources/accounts.txt";
	private View view;
	private Model model;
	private Instance mule;
	private Vector<Instance> fighters;

	public Controller() {
		this.view = new View();
		this.model = new Model();
		this.mule = null;
		this.fighters = new Vector<Instance>();
		loadAccountsList();
		new StartListener(this.view.menuItem);
		new RunMuleButtonListener(this.view.runMuleButton);
	}

	private void loadAccountsList() {
		try {
			BufferedReader buffer = new BufferedReader(new FileReader(accountsFilePath));
			String[] splitLine;
			String line;
			line = buffer.readLine();
			while(line != null) {
				splitLine = line.split(" ");
				model.accounts.put(splitLine[0], line);
				JMenuItem account = new JMenuItem(splitLine[0]);
				view.accountsListItems.add(account);
				view.accountsMenu.add(account);
				new AccountItemListener(account);
				line = buffer.readLine();
			}
			buffer.close();
		} catch(Exception e) {
			e.printStackTrace();
		}
	}
	
	// type = true si c'est une mule
	private Instance createCharacterFrame(boolean type, String login, String password, int serverId) {
		CharacterFrame frame = new CharacterFrame(login);
		Instance instance = new Instance(type, login, password, serverId, frame);
		frame.instanceId = instance.id;
		if(!type) {
			fighters.add(instance);
			if(this.mule != null) // la mule est connect�e
				instance.setMule(this.mule);
		}
		model.instances.put(instance.id, instance);
		view.addCharacterFrame(frame);
		frame.addInternalFrameListener(new CharacterFrameListener());
		frame.setVisible(true);
		return instance;
	}

	private void killInstance(JInternalFrame graphicalFrame) {	
		Instance instance = this.model.instances.get(this.view.getInstance(graphicalFrame).instanceId);
		this.view.removeCharacterFrame(graphicalFrame);
		if(instance == this.mule) {
			this.model.removeMuleToEveryFighter(this.mule);
			this.mule = null;
		}
		else
			this.fighters.remove(instance);
		Instance.killInstance(instance);
	}
	
	private class RunMuleButtonListener implements ActionListener {
		private RunMuleButtonListener(AbstractButton button) {
			button.addActionListener(this);
		}

		public void actionPerformed(ActionEvent event) {
			mule = createCharacterFrame(true, "nicomarchand", "poupinou47", 11);
			model.assignMuleToEveryFighter(mule);
		}
	}

	private class StartListener implements ActionListener {
		private StartListener(AbstractButton button) {
			button.addActionListener(this);
		}

		public void actionPerformed(ActionEvent event) {
			new ConnectionListener(view.createLoginPanel());
		}
	}

	private class ConnectionListener implements ActionListener {
		private LoginPanel loginPanel;

		private ConnectionListener(LoginPanel loginPanel) {
			this.loginPanel = loginPanel;
			this.loginPanel.connectButton.addActionListener(this);
		}

		public void actionPerformed(ActionEvent event) {
			String login = this.loginPanel.loginField.getText();
			String password = this.loginPanel.passwordField.getText();
			int serverId = Integer.parseInt(this.loginPanel.serverField.getText());
			if(login.isEmpty() || password.isEmpty() || serverId == 0)
				JOptionPane.showMessageDialog(null, "Missing informations.", "Erreur", JOptionPane.ERROR_MESSAGE);
			else {
				createCharacterFrame(false, login, password, serverId);
				loginPanel.dispose();
				try {
					// ajout du compte dans le fichier de sauvegarde
					if(model.accounts.get(login) == null) {
						BufferedWriter buffer = new BufferedWriter(new FileWriter(accountsFilePath, true));
						buffer.write(login + " " + password + " " + serverId);
						buffer.newLine();
						buffer.close();
						model.accounts.put(login, login + " " + password + " " + serverId);
						JMenuItem account = new JMenuItem(login);
						view.accountsListItems.add(account);
						view.accountsMenu.add(account);
						new AccountItemListener(account);
					}
				} catch(Exception e) {
					e.printStackTrace();
				}
			}
		}
	}

	private class CharacterFrameListener implements InternalFrameListener {

		@Override
		public void internalFrameActivated(InternalFrameEvent arg0) {}

		@Override
		public void internalFrameClosed(InternalFrameEvent arg0) {}

		@Override
		public void internalFrameClosing(InternalFrameEvent event) {
			killInstance(event.getInternalFrame());
		}

		@Override
		public void internalFrameDeactivated(InternalFrameEvent arg0) {}

		@Override
		public void internalFrameDeiconified(InternalFrameEvent arg0) {}

		@Override
		public void internalFrameIconified(InternalFrameEvent arg0) {}

		@Override
		public void internalFrameOpened(InternalFrameEvent arg0) {}
	}

	private class AccountItemListener implements ActionListener {
		private JMenuItem accountItem;

		private AccountItemListener(JMenuItem item) {
			this.accountItem = item;
			this.accountItem.addActionListener(this);
		}

		public void actionPerformed(ActionEvent e) {
			String[] connectionInfos = model.accounts.get(this.accountItem.getText()).split(" ");
			createCharacterFrame(false, connectionInfos[0], connectionInfos[1], Integer.parseInt(connectionInfos[2]));
		}
	}
}