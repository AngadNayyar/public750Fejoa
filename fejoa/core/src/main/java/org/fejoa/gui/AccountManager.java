/*
 * Copyright 2016.
 * Distributed under the terms of the GPLv3 License.
 *
 * Authors:
 *      Clemens Zeidler <czei002@aucklanduni.ac.nz>
 */
package org.fejoa.gui;

import org.fejoa.library.support.ArrayListModel;
import org.fejoa.library.support.WeakListenable;

import java.io.File;


public class AccountManager extends WeakListenable<AccountManager.IListener> {
    public interface IListener {
        void onAccountSelected(Account account);
    }

    final private ArrayListModel<Account> accountList = new ArrayListModel<>();
    final private File baseDir;
    static final private String ACCOUNT_DIR = "accounts";
    private Account selectedAccount = null;

    public AccountManager(File baseDir) {
        this.baseDir = baseDir;
        File accountDir = getAccountDir();
        if (!accountDir.exists())
            accountDir.mkdirs();

        for (File dir : accountDir.listFiles()) {
            if (dir.isFile())
                continue;
            accountList.add(new Account(dir));
        }
    }

    public File getAccountDir() {
        return new File(baseDir, ACCOUNT_DIR);
    }

    public ArrayListModel<Account> getAccountList() {
        return accountList;
    }

    public void setSelectedAccount(Account account) {
        this.selectedAccount = account;
        for (IListener listener : getListeners())
            listener.onAccountSelected(account);
    }

    public Account getSelectedAccount() {
        return selectedAccount;
    }
}
